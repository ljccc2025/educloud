# EduCloud M05 Course 模块设计规格

> 日期：2026-08-23
>
> 状态：设计草案（待用户审查）
>
> 模块：M05 `educloud-course`
>
> 交付类型：可独立运行的 Spring MVC 业务服务（课程权威数据、维护与学习关系）

## 1. 目的与前置条件

M05 交付课程权威数据与学习关系：课程分类、课程根与不可变版本、教师关系、审核状态机、生命周期（下架/重新上架/归档）、免费选课、我的课程、课程学生列表与课程评价；并与三门户前端联调替换课程相关 mock。

前置条件（均已满足）：

- M01 `educloud-common`：统一响应、错误基类、requestId/traceId、EventEnvelope、Outbox 基础设施（`OutboxWriter`/`OutboxEventDispatcher` 复用 M03/M04 模式）。
- M02 `educloud-gateway`：`RouteGroups.COURSE` 组已预留（`/api/v1/me/enrollments`、`/api/v1/categories/**`、`/api/v1/course-drafts/**`、`/api/v1/course-audits/**`、`/api/v1/courses/**`、`/api/v1/course-reviews/**`、`/api/v1/admin/courses`、`/api/v1/teacher/courses`、`/api/v1/teacher/courses/*/draft`）；路由 `course-core`（order 70）与 `course-enrollments`（order 40）已指向 `lb://educloud-course`；`AccessPolicy.PUBLIC_READ` 已放行 `GET /api/v1/categories`、`GET /api/v1/courses`、`GET /api/v1/courses/{courseId}`。
- M03 `educloud-user`：JWT RS256 + JWKS、服务令牌签发（`client_credentials`）、RBAC（角色/权限/角色权限关联表）、`audit_event.actor_type` 已扩到 VARCHAR(32)。
- M04 `educloud-file`：上传会话、受控绑定（ownerService 由 clientId 推导）、批量下载授权已支持 `PUBLIC_CATALOG` purpose + `ANONYMOUS` subject（课程封面直接复用）；`GrantPurposePolicy` 白名单可配置。

## 2. 已比较方案与决定

| 方案 | 说明 | 决定 |
|---|---|---|
| **A（采纳）** | 复刻 M04 工程模式；封面复用 `PUBLIC_CATALOG`（File 零语义改动，仅 OwnerServiceRegistry 注册 `educloud-course` clientId）；前端按 student → teacher → admin 分阶段联调 | 回归面最小，与交接文档联调节奏一致 |
| B（未采纳） | File 新增 `COURSE_COVER` purpose | 语义更精确但扩大 File 白名单改动面与回归范围 |
| C（未采纳） | 封面仅存 fileId 不签名 | 违反 §8 封面 URL 契约，演示体验差 |

## 3. 范围与边界

**范围（M05 交付）：**

- 课程分类树（匿名可见）。
- 课程根与不可变版本：草稿/待审/驳回/发布/已下线/已归档/已废弃生命周期；价格（十进制金额）、难度、封面 fileId。
- 课程教师关系：负责人与共同授课教师；归属校验硬规则。
- 审核状态机：提交审核、审批（同事务原子切换发布版本）、驳回（原因必填）、撤回；审核角色不能审批自己的提交。
- 生命周期操作：下架、重新上架（OFFLINE 且有有效发布版本）、归档（必须先下架，不可再销售）。
- 学习关系：免费课程幂等选课、我的课程、教师课程学生列表。
- 课程评价：已选课学生创建/更新（rating 1-5）、管理端隐藏 API；评分/选课数展示汇总列。
- 封面 File 集成：bind/unbind + 每页至多一次批量 grant（ANONYMOUS/PUBLIC_CATALOG）。
- RBAC 扩展：9 个 `course:*` 权限码、`COURSE_REVIEWER` 内置角色、角色权限关联。
- 种子数据：分类树 + 已发布/草稿/待审演示课程 + 选课与评价种子。
- 三门户前端联调：student（列表/详情/我的课程/选课/评价）→ teacher（课程管理/编辑/封面上传/提交审核/学生列表）→ admin（课程审核/上下架/归档）。

**非目标（明确排除，写入本规格避免越界）：**

- 章节课件、学习进度、作业、考试、社区（M06 `educloud-content`；网关已将 `/courses/{id}/chapters/**` 等路由给 content）。学生端 CourseDetail 章节区显示「目录即将上线」占位，隐藏 mock。
- 付费课程购买、购物车、订单（M07 `educloud-order`）；Checkout 页保持 mock。
- 支付、退款（M08）。
- `course_content_readiness_projection` 激活（M06 消费 `ContentRevisionPublished` 后启用；M05 仅建表 + 预留消费骨架）。M05 的 republish 就绪 gate 恒放行。
- Redis 课程缓存（M12 性能阶段；当前课程规模小，YAGNI）。
- 评价管理端 UI（API 先行，UI 后补）。
- 分类管理 UI 与管理 API（分类以种子 + 数据库维护；管理 API 归 M12 运营面）。

## 4. 架构总览

- Spring MVC 服务：对外端口 **8089**、管理端口 **8090**（顺延 M04 file 8087/8088；后续 M06 content 从 8092/8093 起分配，避开旧架构表端口冲突）。
- JWT Resource Server：复用 user 公钥 JWKS（kid 约定一致），权限码 `course:xxx`。
- MyBatis-Plus 3.5.12（分页 + 乐观锁拦截器，含 `mybatis-plus-jsqlparser`）；乐观锁拦截器写回新 version，**禁止手动 +1**（M04 坑 4）。
- Nacos 注册（账号 `educloud_course`，API 方式 provision，无 admin 模式）。
- RabbitMQ Outbox：复用 common 模式，TopicExchange `educloud.events`，routing key `aggregateType.aggregateId`（点号）。
- 内部面：`/internal/v1/**` 仅服务令牌可达（InternalApiFilter + OwnerServiceRegistry）；M05 最小实现 `GET /internal/v1/courses/{id}` 可见性/归属查询（M06 content 消费）。
- 服务间调用：course → File（服务令牌 aud=educloud-file, scope=file:internal）。

## 5. 数据模型（`educloud_course` 逻辑库）

### 5.1 技术表（V000，复刻 file 模式）

`outbox_event`、`inbox_event`、`outbox_sequence`、`audit_event`（actor_type VARCHAR(32)）、`idempotency_record`、`schema_migration_history`。

### 5.2 业务表（V001）

**`course_category`**：`id`、`parent_id`（自关联）、`name`、`slug`（唯一）、`sort_order`、`status`、审计字段。公开读取只返回可见分类。

**`course`**（聚合根）：`id`、`owner_teacher_id`、`lifecycle_status`（DRAFT/PENDING_REVIEW/PUBLISHED/OFFLINE/ARCHIVED）、`published_version_id`、`draft_version_id`、`published_at`、`rating_avg`、`rating_count`、`enrollment_count`、`version`（乐观锁）、审计字段。公开读取只跟随 `published_version_id`；教师编辑只操作 `draft_version_id`。索引：`idx_course_owner_status`、`idx_course_published_at`。

**`course_version`**（不可变版本）：`id`、`course_id`、`version_no`、`category_id`、`title`、`subtitle`、`description`、`cover_file_id`、`level`（BEGINNER/INTERMEDIATE/ADVANCED）、`price`（DECIMAL(10,2)）、`currency`、`version_status`（DRAFT/PENDING_REVIEW/REJECTED/PUBLISHED/SUPERSEDED/WITHDRAWN）、`content_hash`、`created_by`、`created_at`。唯一索引 `(course_id, version_no)`。提交审核后不可原地修改；审核通过与发布在同一本地事务完成，不保留无业务语义的中间 APPROVED 状态。

**`course_teacher`**：`id`、`course_id`、`teacher_id`、`teacher_role`（OWNER/CO_TEACHER）、`joined_at`。唯一索引 `(course_id, teacher_id)`。归属校验（负责人或共同授课）是服务内硬规则。

**`course_audit_submission`**：`id`、`course_id`、`course_version_id`（唯一）、`status`（PENDING/APPROVED/REJECTED/WITHDRAWN）、`submitted_by`、`submitted_at`、`withdrawn_at`、`reviewed_by`、`reviewed_at`、`reason`。审批时原子切换 `course.published_version_id`，旧发布版本置 `SUPERSEDED`；驳回时 `reason` 必填。

**`course_enrollment`**：`id`、`course_id`、`student_id`、`source`（FREE/ORDER）、`source_order_id`、`status`（ACTIVE/REVOKED）、`enrolled_at`、`access_ended_at`、`revoke_reason`、`version`。唯一索引 `(course_id, student_id)`、索引 `(student_id, status)`。`EnrollmentCreated`/`EnrollmentRevoked` 事件使用 `aggregateType=Enrollment`、`aggregateId=id` 与行内递增 `version`。

**`course_content_readiness_projection`**：`id`、`course_id`、`content_root_id`、`published_revision_id`、`ready`、`source_event_id`、`last_aggregate_version`、`updated_at`。`course_id` 与 `source_event_id` 唯一。M05 仅建表 + 消费骨架（Inbox 幂等落表、不激活 gate）。

**`course_review`**：`id`、`course_id`、`student_id`、`rating`（1-5）、`content`、`status`（VISIBLE/HIDDEN）、审计字段。唯一索引 `(course_id, student_id)`（upsert 语义）。`course.rating_avg/rating_count` 为展示汇总列，评价写事务内同步更新，评价表仍是事实来源。

### 5.3 种子数据（V002）

- 分类树：3 个顶级分类（前端开发/后端开发/数据分析）+ 各 2 个子分类，`slug` 唯一、`sort_order` 有序。
- 演示课程 6 门已发布（`lifecycle_status=PUBLISHED`）：价格含免费 2 门 + 付费 4 门、难度覆盖三级、评分种子 3.8-4.9、归属 demo_teacher 1 门 + demo_admin 名下教师 1 门 + 其他教师账号；另 seed demo_teacher 名下 DRAFT 草稿 1 门 + PENDING_REVIEW 1 门。
- 选课种子：fe_demo_10 已选 2 门免费课程（我的课程可演示）+ demo_student_2（预留学生 ID 9000000000000000201）选 110，为其第二条评价提供 ACTIVE 选课依据。
- 评价种子：3 条（110 课程 5+4 → avg 4.50、111 课程 4 → avg 4.00，全部落在评分区间 [3.8, 4.9] 且与明细一致）。
- 封面 `cover_file_id` 初始为空 → 前端卡片兜底图；VM 演示时教师端真实上传封面走完整链路。

> **执行备注（规格审查 2026-08-24 记录）：**
> 1. 教师归属：`seed-demo-users.sh` 仅创建 demo_teacher / demo_admin，无其他 TEACHER 账号，故 V002 种子课程（含 DRAFT/PENDING_REVIEW）当前全部归属 demo_teacher；归属分散（其他教师账号）待后续扩展账号后以 V003 调整。
> 2. VM 部署序列：user 迁移（V000-V004，含 `course:*` 权限）→ `seed-demo-users.sh`（demo_teacher/demo_admin）→ 确认 fe_demo_10 学生存在 → course 迁移（V000-V002）。
> 3. demo_student_2 为 course 库侧预留引用（无外键约束），若教师学生列表需展示其用户名，需先在 user 库补建同名账号。

## 6. API 契约（23 端点，含任务 22/23 计划外补齐，全部经网关）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/categories` | 匿名 | 可见分类树（分页不需要，全量有序） |
| GET | `/courses` | 匿名 | 已发布分页列表（管理全状态查询走 `GET /admin/courses`） |
| GET | `/courses/{id}` | 按可见性 | 课程详情（已发布公开；教师本人可见自己的草稿/待审） |
| POST | `/courses` | `course:create` | 教师创建草稿 |
| GET | `/teacher/courses` | `course:update` + 归属 | 教师课程管理列表（任务 22 计划外补齐）：归属教师的全部课程含生命周期/版本状态分页；当前工作版本 COALESCE(draft_version_id, published_version_id, 最新版本) 驱动（撤回后指针清空仍可列出），封面按页 USER grant |
| GET | `/teacher/courses/{id}/draft` | `course:update` + 归属 | 返回当前可编辑草稿，不影响公开版本；无活动草稿（含撤回后指针清空）404 |
| GET | `/admin/courses` | `course:audit` | 管理端全状态课程列表（任务 23 计划外补齐）：全部生命周期（DRAFT/PENDING_REVIEW/PUBLISHED/OFFLINE/ARCHIVED）分页，`lifecycleStatus` 可选过滤；当前工作版本 COALESCE(draft_version_id, published_version_id, 最新版本) 驱动，封面按页 USER grant |
| POST | `/courses/{id}/drafts` | `course:update` + 归属 | 从发布/驳回/已撤回版本复制新草稿（WITHDRAWN 纳入复制源，撤回恢复路径） |
| PUT | `/course-drafts/{versionId}` | `course:update` + 归属 | 只更新 DRAFT 版本（全量字段） |
| POST | `/course-drafts/{versionId}/submit-review` | `course:submit` + 归属 | 提交不可变版本审核 → PENDING_REVIEW |
| GET | `/course-audits` | `course:audit` | 管理端待审核课程分页 |
| GET | `/course-audits/{id}` | `course:audit` | 审核快照和历史 |
| POST | `/course-audits/{id}/approve` | `course:audit` | 审批通过（同事务发布） |
| POST | `/course-audits/{id}/reject` | `course:audit` | 驳回，原因必填 |
| POST | `/course-audits/{id}/withdraw` | 提交教师 | 审核前撤回并使版本不可再审批；同时清空 course.draft_version_id（编辑页经 GET draft 404 落入重建草稿恢复路径） |
| POST | `/courses/{id}/offline` | `course:offline` | 下架（PUBLISHED → OFFLINE） |
| POST | `/courses/{id}/republish` | `course:republish` | 仅 OFFLINE 且有有效发布版本；M05 就绪 gate 恒放行 |
| POST | `/courses/{id}/archive` | `course:archive` | 归档且不可重新销售；已发布课程必须先下架 |
| POST | `/courses/{id}/enrollments` | `course:enroll` | 免费课程选课（幂等） |
| GET | `/me/enrollments` | 学生 | 我的课程（课程信息 + 选课状态） |
| GET | `/courses/{id}/students` | `course:student:read` + 归属 | 教师学生列表 |
| POST | `/courses/{id}/reviews` | 已选课学生 | 创建/更新自己的评价（upsert） |
| DELETE | `/course-reviews/{id}` | 管理角色 | 隐藏评价（status → HIDDEN；UI 后补） |

**契约要点：**

- 所有 Snowflake ID（courseId/versionId/auditId/coverFileId/enrollmentId/reviewId）在 DTO 一律 String；前端禁止 Number()（M04 坑 1）。
- `GET /courses` 查询参数：`keyword`、`categoryId`、`level`、`priceRange`（free/under200/200to400/above400）、`sort`（popular/newest/price-asc/price-desc/rating 白名单）、`page`、`size`（公开目录仅已发布，匿名）；管理全状态查询不在公开端点加 `status` 参数（网关 PUBLIC_READ 匿名放行、CourseListQuery 无 status），改由 `GET /admin/courses`（`course:audit`）承载（任务 23 计划外补齐）。
- 课程列表项：`id`、`title`、短期 `coverUrl`、教师展示名、分类、难度、价格、评分（avg/count）、选课数、`enrolled`（已登录用户是否已选）。已发布课程封面每页至多一次 File 批量 grant（ANONYMOUS + PUBLIC_CATALOG）；未通过可见性校验的课程不签封面，不能以伪造 courseId/ownerId 取得草稿/下架课程文件。
- 课程详情：`id`、`title`、`subtitle`、`description`、`coverUrl`、`level`、`price`、`currency`、`category`、`teachers`（负责人+共同授课）、`ratingAvg`、`ratingCount`、`enrollmentCount`、`enrolled`、`lifecycleStatus`（教师本人视角可含 DRAFT/PENDING_REVIEW）、`reviews`（可见评价列表，分页）。不泄露内部审核快照（course_version 不可变版本内部字段、audit submission 内部字段不下发）。
- 错误码（对齐 CommonErrorCode 模式）：`COURSE_NOT_FOUND` 404、`COURSE_NOT_FREE` 409、`COURSE_OFFLINE_OR_ARCHIVED` 409（选课目标不可用）、`VERSION_NOT_DRAFT` 409、`SUBMISSION_NOT_PENDING` 409、`REVIEW_REJECT_REASON_REQUIRED` 400、`NOT_ENROLLED` 403（评价）、`COURSE_ACCESS_DENIED` 403（归属/越权）、`REVIEW_NOT_FOUND` 404。

## 7. 核心流程

**建课 → 发布闭环：** 教师 `POST /courses`（建 course 根 DRAFT + 首个 DRAFT 版本）→ `PUT /course-drafts/{versionId}` 编辑（全量字段，含封面 bind）→ `submit-review`（版本置 PENDING_REVIEW，不可改）→ 管理员 `approve`（同事务：submission APPROVED、course.published_version_id 切换、旧发布版本 SUPERSEDED、lifecycle_status=PUBLISHED、发 `CoursePublished`）或 `reject`（REJECTED + 原因；教师 `POST /courses/{id}/drafts` 从驳回复制新草稿再改再提）或教师 `withdraw`（PENDING → WITHDRAWN）。

**版本乐观锁：** `course.version` 在审批/生命周期切换时 `SELECT ... FOR UPDATE` 锁根 + 拦截器写回新 version；并发提交/审批以锁 + version 保证原子性。

**免费选课：** `POST /courses/{id}/enrollments` → 校验课程 PUBLISHED 且免费（付费 409 COURSE_NOT_FREE）→ 幂等（已 ACTIVE 返回现状 200）→ 插入/恢复 enrollment + `enrollment_count` 递增 + 发 `EnrollmentCreated`。M05 无撤销触发路径（`EnrollmentRevoked` 事件与 REVOKED 状态预留，M07 退款接入）。

**评价：** 已选课学生 `POST /courses/{id}/reviews`（校验 ACTIVE 选课，未选课 403）→ upsert `course_review`（唯一约束）→ 同事务重算 `course.rating_avg/rating_count`；管理角色 `DELETE /course-reviews/{id}` 置 HIDDEN 并重算。

**封面：** 教师保存草稿时（PUT）携带 `coverFileId`（来自前端 uploadCover 完整链路：创建会话 → presigned PUT → complete），Course 校验 uploader 属主后调 File `bind`（ownerService=course、ownerType=COURSE、ownerId=courseId）；版本替换时解绑旧封面。列表/详情 grant 见 §6。

## 8. 事件与 Outbox

- Exchange `educloud.events`（TopicExchange，vhost=educloud）；routing key 点号 `aggregateType.aggregateId`；队列绑定 `Course.#`、`Enrollment.#`（M05 无消费者，M06+ 的 Search/Analytics/Recommendation 后续接入）。
- 事件：`CoursePublished`、`CourseOfflined`、`CourseRepublished`、`CourseArchived`（aggregateType=Course）、`EnrollmentCreated`、`EnrollmentRevoked`（aggregateType=Enrollment，M05 预留触发路径）。
- 载荷：EventEnvelope（M01 模式：eventId、aggregateType、aggregateId、aggregateVersion、occurredAt、payload）。Outbox 事务内写入，后台分发（复用 M03/M04 实现，失败退避 + 达阈值 FAILED）。

## 9. 安全设计

- JWT Resource Server（`course:*` 权限码）；内部接口 InternalApiFilter（clientId → ownerService 推导，未知 clientId 403）。内部控制器（任务 17）只用 `InternalApiFilter.requireClientId` 取调用方身份，不配 `@PreAuthorize`（服务令牌无 permissions claim）。
- 归属校验硬规则：草稿读/改/提交、学生列表、下架/归档/重上架均须 `course_teacher`（OWNER 或 CO_TEACHER）；`course:audit` 角色不能审批自己的提交。
- 信任边界（对齐 M04 坑 5）：封面 bind 前校验上传者属主；grant 只对通过可见性校验的课程组装，匿名 `PUBLIC_CATALOG` 只能签已发布课程封面，禁止伪造 courseId/ownerId 取草稿/下架文件。
- 越权门禁用例（验收必测）：教师 A 读教师 B 草稿 403；未选课学生写评价 403；学生查他人课程学生列表 403；匿名访问未发布课程 404/403；伪造封面 fileId 绑定 403；审核角色自审 403。

## 10. 配置项（`educloud.course.*`，env 可覆盖）

- `server.port` 8089、`management.server.port` 8090（start-dev 注入 `COURSE_MANAGEMENT_PORT`）。
- MYSQL/REDIS/RABBIT/NACOS：与 user/file 同源（`EDUCLOUD_COURSE_DB_PASSWORD`、`EDUCLOUD_COURSE_MIGRATION_PASSWORD`、`NACOS_COURSE_USERNAME/PASSWORD`）。
- JWT：`COURSE_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json`、`EDUCLOUD_COURSE_JWT_ISSUER/AUDIENCE`（对齐 user/gateway/file）。
- FileClient：`educloud.course.file.endpoint/client-id/client-secret/enabled`（默认 http://127.0.0.1:8087、educloud-course、true）；批量 grant 上限 100；令牌缓存提前 30s 刷新（复刻 user FileClient）。
- 分页默认 `page=1 size=20`，`size` 上限 100；排序白名单见 §6。

## 11. 可观测性

- 管理端口 readiness 组：mysql/redis/rabbit/nacos 依赖健康检查（复刻 user/file 模式）。
- 业务指标：课程发布数、选课数、审核通过/驳回数（Micrometer 计数）。
- 审计：关键写操作（建课/提交审核/审批/驳回/下架/归档/选课/评价隐藏）写 `audit_event`（actor_type 存角色名或 USER，VARCHAR(32) 已兼容）。

## 12. 测试策略

- **单元**：课程版本状态机（DRAFT→PENDING_REVIEW→REJECTED/PUBLISHED→SUPERSEDED/WITHDRAWN 全转移 + 非法转移拒绝）、审核状态机（approve/reject/withdraw 前置状态校验、自审拒绝）、归属校验（跨教师 403）、免费选课幂等（并发重复选课单行）、付费课程拒绝、评价范围（未选课 403、HIDDEN 不可见）、列表过滤/排序白名单/分页、乐观锁冲突（version 不符 409）、FileClient 403/404 语义映射（MockRestServiceServer）。
- **集成**（Testcontainer MySQL，`-Pintegration`）：CourseSchemaIT（8 业务表 + 技术表结构）、CoursePublishFlowIT（提交→审批→发布原子切换 + Outbox 落库 + 事件信封）、EnrollmentConcurrencyIT（并发选课幂等）、ReviewSummaryIT（upsert + 汇总一致性）。
- **File 集成**：VM e2e 真实链路（教师上传封面 → bind → 学生列表/详情 grant 出 URL → 下架后匿名不可取）。
- **回归**：`mvn -pl educloud-common,educloud-gateway,educloud-user,educloud-file,educloud-course -am verify` 全绿（M04 门禁基线不破）。

## 13. 前端接入（分阶段联调，已确认）

**阶段 1 student：** 新增 `services/courseApi.ts`（真实 API，复用 http.ts 拦截器与错误映射）；`useCourseStore` 接真实接口；CourseList（真实分页/筛选/排序/封面兜底）、CourseDetail（真实信息 + 评价展示/提交 + 章节区「目录即将上线」占位）、MyCourses（真实列表 + 免费选课/付费提示）；types 对齐真实 DTO（Snowflake 字符串，禁止 Number()）。

**阶段 2 teacher：** CourseManage（真实状态列表）、CourseEdit（真实表单 + 封面上传复用 file.ts 模式改 uploadCover + 提交审核）、StudentList（真实学生列表）。

**阶段 3 admin：** CourseAudit（待审分页/快照/批准/驳回原因必填）；上下架/归档操作入口。

**约束：** 三门户已有 http.ts/登录/令牌注入（M03 联调完成）；无 mock 回退（user 未就绪显示加载占位，对齐 M04 前端纪律）；登录后 `/me` 补拉（既有）。

## 14. 门禁与验证清单

- [x] 读取 M05 相关设计文档（本规格 + services-and-domains/data-design/api-and-integration 第 8 节）并审阅 diff
- [x] 先写失败测试并确认失败
- [x] 实现最小后端能力（模块测试绿）
- [x] 集成测试（Testcontainer MySQL）绿
- [x] 全量 `mvn verify` 绿
- [x] 规格审查与质量审查（含独立代码审查：越权/状态机/幂等/评价范围/封面信任边界测试通过）
- [x] VM 部署：迁移 `educloud_course`、Nacos provision、RBAC V004、bootstrap service client、start-dev 拉起
- [x] 浏览器验证：三门户课程闭环（建课→审核→发布→选课→评价）+ 越权用例全 403
- [x] 向用户汇报并等待确认后进入 M06

## 15. 已知决策点（实现中若遇冲突回本表）

| 决策点 | 默认选择 | 说明 |
|---|---|---|
| Course 端口 | 对外 8089 / 管理 8090 | 顺延 file 8087/8088；M06 content 从 8092/8093 起 |
| 封面授权 | 复用 PUBLIC_CATALOG + ANONYMOUS/USER | File 零语义改动，仅注册 course clientId |
| 内容就绪 gate | M05 恒放行 | `course_content_readiness_projection` 建表不激活，M06 启用 |
| 种子封面 | 空 → 前端兜底图 | VM 演示时真实上传覆盖 |
| 评价隐藏 | API 先行（DELETE 置 HIDDEN），管理 UI 后补 | |
| 管理隐藏权限（长期） | V005 新增 `course:review:hide` 权限码 + `@PreAuthorize` 接入 | 当前 `course:*` 无 review 专用码，服务层按 JWT roles claim 硬判 SYSTEM_ADMIN/SUPER_ADMIN（对齐 TeacherAccessGuard 服务内硬规则）；是否授 COURSE_REVIEWER 隐藏权待产品定 |
| 隐藏后学生再评价 | upsert 固定写 status=VISIBLE，软隐藏可被学生重新评价覆盖（维持现状） | 已文档化（CourseReviewService 注释）并有单测/IT 锁定（P1b 窄更新 + P3 归一化）；不做「隐藏后禁止复活」 |
| Redis 缓存 | M05 不做 | 课程规模小，M12 性能阶段再评估 |
| EnrollmentRevoked | 状态与事件预留，无触发路径 | M07 订单退款接入 |
| 审核中间态 | 不保留 APPROVED | 审核通过与发布同一本地事务 |
| 分类管理 | 种子 + 数据库维护 | 管理 API 归 M12 运营面 |
| 时间字段类型 | LocalDateTime（对齐 DATETIME(3) 本地时间语义） | file 模块实体用 Instant 为既有差异；Course 模块统一 LocalDateTime |
| advice 共存顺序 | Course handler `@Order(LOWEST_PRECEDENCE - 1)` 先于 common（无 @Order ≈ LOWEST_PRECEDENCE） | Spring 异常解析为「首个匹配 advice 获胜」（非跨 advice 特异度）：域 advice 必须靠前，其 BusinessException/AccessDenied 处理器才不被 common 的 Exception 兜底抢先吞成 500（file 模块同类共存顺序问题即因此产生，M04 已验收，留待独立评审）；坏 JSON/缺参/404/405/未知 500 等共享错误 course 无匹配时仍由 common 的 400/404/405/500 语义处理 |
| JWKS 强制字段 | use=sig + alg=RS256 | course 与 file 同策略强制（JwksLoader 校验）；user 侧 generate-user-jwt-keys.sh 输出需含此二字段，联调时验证 |
| 归档终态门禁 | 建草稿/提交/审批三层拦截 | ARCHIVED 为终态（归档且不可重新销售）；`createDraftFromPublishedOrRejected`/`submitForReview`/`approve` 对 lifecycle=ARCHIVED 一律 409 COURSE_STATE_CONFLICT，防止绕过 republish 的 OFFLINE 门禁复活 |

## 16. 参考文档

- [模块执行顺序与准备门禁](./2026-08-20-educloud-backend-module-execution.md)（M05 边界与门禁）
- [服务边界与领域模块](./2026-08-18-educloud-services-and-domains.md)（第 3 节 Course）
- [数据库与数据设计](./2026-08-18-educloud-data-design.md)（第 4 节 Course 数据库）
- [API、事件与前后端联调](./2026-08-18-educloud-api-and-integration.md)（第 8 节 Course API）
- [认证、权限与安全](./2026-08-18-educloud-security-and-permissions.md)（课程权限、文件信任边界）
- [架构与栈](./2026-08-18-educloud-architecture-and-stack.md)（服务端口演进）
- [M04 模块设计规格](./2026-08-22-educloud-file-design.md)（File 集成契约与工程模式）
- [后端实施路线图](./2026-08-18-educloud-backend-roadmap.md)（Task 13/14/15、Task 21 课程部分）
