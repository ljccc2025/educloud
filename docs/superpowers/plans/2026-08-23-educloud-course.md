# EduCloud M05 educloud-course 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（\`- [ ]\`）语法跟踪进度。

**目标：** 交付 \`educloud-course\` 服务：课程分类、课程根与不可变版本、教师关系、审核状态机、生命周期（下架/重上架/归档）、免费选课、我的课程、课程学生列表与课程评价；封面接入 M04 File 服务；三门户前端分阶段替换课程 mock。

**架构：** Spring MVC 服务（对外 8089 / 管理 8090），对外 API 经 Gateway（Bearer + \`course:*\` 权限码），内部 API 用服务令牌（aud=educloud-file）；MyBatis-Plus（分页+乐观锁）；课程审核通过与发布同一本地事务原子切换；封面复用 File \`PUBLIC_CATALOG\` 批量 grant；Outbox 发布 Course/Enrollment 域事件。

**技术栈：** Spring Boot 3.2.5、MyBatis-Plus 3.5.12（含 mybatis-plus-jsqlparser）、MySQL 8.0.36（\`educloud_course\`）、Redis/RabbitMQ/Nacos（复用 M03/M04 模式）、Testcontainers。

**规格：** \`docs/superpowers/specs/2026-08-23-educloud-course-design.md\`（已批准）｜执行契约：\`docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md\`

---

## 文件结构（锁定分解）

\`\`\`text
educloud-backend/educloud-course/
├── pom.xml                                    # 依赖：common/web/security/oauth2-resource-server/
│                                              # mybatis-plus/redis/rabbit/nacos/actuator/micrometer/testcontainers
└── src/
    ├── main/java/com/educloud/course/
    │   ├── CourseApplication.java             # 启动类（@SpringBootApplication + @MapperScan）
    │   ├── config/
    │   │   ├── MybatisPlusConfig.java         # 分页+乐观锁拦截器（禁手动 +1）
    │   │   ├── SecurityConfig.java            # JWT Resource Server（JWKS 复用 user 公钥）
    │   │   ├── InternalApiFilter.java         # /internal/v1/** 服务令牌校验（aud=educloud-course）
    │   │   ├── CourseFileProperties.java      # educloud.course.file.* 配置
    │   │   ├── OutboxConfig.java              # OutboxWriter/EventDispatcher Bean
    │   │   └── RabbitConfig.java              # TopicExchange educloud.events（vhost=educloud）
    │   ├── controller/
    │   │   ├── CategoryController.java        # GET /categories
    │   │   ├── CourseCatalogController.java   # GET /courses、GET /courses/{id}
    │   │   ├── CourseTeacherController.java   # POST /courses、GET draft、POST drafts、PUT draft
    │   │   ├── CourseAuditController.java     # 审核 5 端点
    │   │   ├── CourseLifecycleController.java # offline/republish/archive
    │   │   ├── EnrollmentController.java      # enrollments、me/enrollments、students
    │   │   ├── CourseReviewController.java    # POST reviews、DELETE course-reviews/{id}
    │   │   └── InternalCourseController.java  # GET /internal/v1/courses/{id}
    │   ├── dto/request/  dto/response/        # 全部 Snowflake ID 序列化为 String
    │   ├── entity/                            # 8 表实体（乐观锁 @Version 只用于 course/enrollment）
    │   ├── exception/                         # CourseErrorCode、CourseExceptionHandler
    │   ├── mapper/                            # 8 Mapper（XML 或注解）
    │   ├── messaging/                         # CourseEventPublisher（Outbox 写入）
    │   ├── observability/                     # 指标 + 审计写入
    │   ├── security/                          # JwtSecurityUtils（subject/权限提取）
    │   ├── service/
    │   │   ├── CategoryService.java
    │   │   ├── CourseService.java             # 建课/草稿/生命周期（锁根）
    │   │   ├── CourseVersionService.java      # 版本 CRUD（仅 DRAFT）
    │   │   ├── CourseAuditService.java        # 审核状态机 + 原子发布
    │   │   ├── EnrollmentService.java         # 幂等免费选课/我的课程/学生列表
    │   │   ├── CourseReviewService.java       # upsert/隐藏/汇总重算
    │   │   ├── CourseCatalogService.java      # 列表/详情 + 可见性 + File grant 组装
    │   │   └── FileClient.java                # 服务令牌缓存 + bind/grant（复刻 user 版）
    │   ├── state/                             # 版本/审核状态机转移表
    │   └── support/                           # 归属校验 TeacherAccessGuard、分页参数校验
    └── test/java/com/educloud/course/
        ├── ...Test.java                       # 单元测试（Mockito）
        └── ...IT.java                         # Testcontainer MySQL 集成测试
deploy/sql/course/V000__technical_tables.sql   # 技术表（复刻 file，GRANT 改 course_app/course_migration）
deploy/sql/course/V001__course.sql             # 8 业务表 + 索引 + GRANT
deploy/sql/course/V002__seed.sql               # 分类/演示课程/选课/评价种子（幂等）
deploy/sql/user/V004__course_permissions.sql   # 9 权限码 + COURSE_REVIEWER + STUDENT/TEACHER/ADMIN 挂载
deploy/scripts/provision-course-nacos.sh       # 复制 provision-file-nacos.sh，账号 educloud_course
deploy/scripts/start-dev.sh                    # 修改：新增 [4/6] educloud-course 段（8089/8090）
deploy/docker-compose/.env.example            # 修改：新增 COURSE 相关变量
educloud-backend/educloud-file/...             # 修改：OwnerServiceRegistry 注册 educloud-course clientId
educloud-backend/educloud-gateway/...          # 不改（路由/放行已预留）
educloud-frontend/student-portal/...           # 阶段 1 联调
educloud-frontend/teacher-portal/...           # 阶段 2 联调
educloud-frontend/admin-portal/...             # 阶段 3 联调
\`\`\`

---

## 任务 0：模块骨架与上下文测试

**文件：**
- 修改：\`educloud-backend/pom.xml\`（modules 增加 educloud-course）
- 创建：\`educloud-backend/educloud-course/pom.xml\`（parent 指向 Backend 父 POM；依赖对齐 educloud-file：common/web/security/oauth2-resource-server/validation/mybatis-plus（含 jsqlparser）/redis/rabbit/nacos-discovery/actuator/micrometer + test：spring-boot-starter-test/testcontainers）
- 创建：\`educloud-backend/educloud-course/src/main/java/com/educloud/course/CourseApplication.java\`
- 创建：\`educloud-backend/educloud-course/src/main/resources/application.yml\`（server.port=8089、management.server.port=8090、spring.application.name=educloud-course、mysql/redis/rabbit/nacos 占位 env、JWKS location env、\`educloud.course.file.*\` 默认值）
- 测试：\`educloud-backend/educloud-course/src/test/java/com/educloud/course/CourseContextTest.java\`（@SpringBootTest + 禁用外部队列/DB 的 profile，断言 context 加载）

- [ ] **步骤 1：写失败测试**：CourseContextTest 断言 \`CourseApplication\` 可加载上下文
- [ ] **步骤 2：运行确认失败**：\`mvn -pl educloud-course -am test\` 预期 FAIL（模块不存在）
- [ ] **步骤 3：实现骨架**：pom.xml/启动类/application.yml（端口 8089/8090；DB/Redis/Rabbit/Nacos 用 env 占位，本地测试 profile 用 H2 或关闭自动配置）
- [ ] **步骤 4：运行测试确认通过**（context 加载绿）
- [ ] **步骤 5：Commit**：\`git commit -m "feat(course): educloud-course 模块骨架与上下文测试"\`

## 任务 1：SQL 迁移 V000 + Schema 集成测试

**文件：**
- 创建：\`deploy/sql/course/V000__technical_tables.sql\`（复制 \`deploy/sql/file/V000__technical_tables.sql\`：schema_migration_history/outbox_event/outbox_sequence/inbox_event/audit_event/idempotency_record；GRANT 全部改 \`course_app\`/\`course_migration\`）
- 测试：\`educloud-backend/educloud-course/src/test/java/com/educloud/course/mapper/CourseSchemaIT.java\`

- [ ] **步骤 1：写失败测试**：断言 \`outbox_event\` 存在、\`inbox_event\` 唯一键 \`uk_inbox_event_id\`、audit_event.actor_type VARCHAR(32)
- [ ] **步骤 2：运行确认失败**：\`mvn -pl educloud-course -am verify -Pintegration -Dtest=CourseSchemaIT\` 预期 FAIL
- [ ] **步骤 3：编写 V000 SQL**（复制 file 版改 GRANT 账号；不写业务表）
- [ ] **步骤 4：运行 IT 确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): educloud_course 技术表迁移与 Schema IT\`

## 任务 2：SQL 迁移 V001（8 业务表）+ Schema 集成测试

**文件：**
- 创建：\`deploy/sql/course/V001__course.sql\`（规格第 5.2 节 8 表 DDL + 索引 + GRANT；DECIMAL(10,2) 价格；Snowflake BIGINT 主键；enrollment/review 唯一键）
- 测试：\`CourseSchemaIT.java\` 扩展

- [ ] **步骤 1：写失败测试**：断言 \`course\`/\`course_version\`/\`course_teacher\`/\`course_audit_submission\`/\`course_enrollment\`/\`course_content_readiness_projection\`/\`course_review\`/\`course_category\` 存在；\`course_version\` 唯一 (course_id, version_no)；\`course_enrollment\` 唯一 (course_id, student_id)；\`course_review\` 唯一 (course_id, student_id)
- [ ] **步骤 2：运行确认失败**（V001 未写）
- [ ] **步骤 3：编写 V001 DDL**（对照规格 5.2 字段与索引；version_status/lifecycle_status 用 VARCHAR + 应用层状态机校验）
- [ ] **步骤 4：运行 IT 确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): educloud_course 业务表迁移与 Schema IT\`

## 任务 3：RBAC 迁移（user 库 V004）与验证

**文件：**
- 创建：\`deploy/sql/user/V004__course_permissions.sql\`（幂等 INSERT：9 个 \`course:*\` 权限码；\`COURSE_REVIEWER\` 内置角色；\`sys_role_permission\` 挂载——COURSE_REVIEWER→course:audit，STUDENT→course:enroll，TEACHER→create/update/submit/offline/republish/archive/enroll/student:read，ADMIN→全部）
- 测试：\`educloud-backend/educloud-user/src/test/java/.../CoursePermissionMigrationIT.java\`（Testcontainer MySQL 执行 V004，断言权限码/角色/关联存在且幂等可重放）

- [ ] **步骤 1：写失败测试**（断言 course:audit 权限码、COURSE_REVIEWER 角色、关联行存在；重复执行 V004 不报错）
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：编写 V004 SQL**（INSERT ... ON DUPLICATE KEY UPDATE 幂等；角色-权限关联同样幂等）
- [ ] **步骤 4：运行 IT 确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): user 库 V004 course 权限码与 COURSE_REVIEWER 角色\`

## 任务 4：实体、Mapper 与 MybatisPlusConfig

**文件：**
- 创建：\`entity/\` 8 个实体（CourseEntity、CourseVersionEntity、CourseCategoryEntity、CourseTeacherEntity、CourseAuditSubmissionEntity、CourseEnrollmentEntity、CourseContentReadinessProjectionEntity、CourseReviewEntity）
- 创建：\`mapper/\` 8 个 Mapper 接口
- 创建：\`config/MybatisPlusConfig.java\`（分页 + 乐观锁拦截器）
- 测试：\`entity/CourseEntityTest.java\`、\`mapper/CourseMapperIT.java\`

- [ ] **步骤 1：写失败测试**：MapperIT 断言 insert/select 往返（Testcontainer）；乐观锁版本回写测试（update 后 version 递增且**不手动 +1**）
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现实体/Mapper/Config**（@TableName/@TableId(ASSIGN_ID)/@Version 仅 course 与 enrollment；表名与 V001 对齐）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 实体 Mapper 与 MyBatis-Plus 配置\`

## 任务 5：错误码与异常处理

**文件：**
- 创建：\`exception/CourseErrorCode.java\`（规格 §6：COURSE_NOT_FOUND 404、COURSE_NOT_FREE 409、COURSE_OFFLINE_OR_ARCHIVED 409、VERSION_NOT_DRAFT 409、SUBMISSION_NOT_PENDING 409、REVIEW_REJECT_REASON_REQUIRED 400、NOT_ENROLLED 403、COURSE_ACCESS_DENIED 403、REVIEW_NOT_FOUND 404）
- 创建：\`exception/CourseExceptionHandler.java\`（@RestControllerAdvice 映射 ApiEnvelope + requestId）
- 测试：\`exception/CourseErrorCodeTest.java\`

- [ ] **步骤 1：写失败测试**（每个错误码 code/httpStatus 断言）
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（对齐 common 的 ErrorCode 接口模式）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 错误码与全局异常处理\`

## 任务 6：安全配置（Resource Server + 内部过滤器）

**文件：**
- 创建：\`config/SecurityConfig.java\`（JWKS 加载 \`file:/tmp/educloud-live/jwks.json\`、issuer/audience env；\`/actuator/health/**\` 匿名、\`/internal/v1/**\` 走 InternalApiFilter、其余需要认证；权限码映射 \`course:create\` 等 9 个）
- 创建：\`config/InternalApiFilter.java\`（复刻 file 版：校验 aud=educloud-course 服务令牌 + clientId 白名单）
- 创建：\`security/JwtSecurityUtils.java\`（subject=userId 解析、permissions 提取）
- 测试：\`security/SecurityConfigTest.java\`（无 token 401、错误 aud 401（Resource Server 层，与 file/gateway 契约一致；服务令牌 aud 不符在 InternalApiFilter 层为 403）、\`course:audit\` 权限拒绝/通过）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现安全配置**（JWKS 路径 env 默认 \`classpath:jwks-test.json\` 供测试；测试 profile 生成测试密钥对——参照 user/file 测试做法）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): JWT Resource Server 与内部过滤器\`

## 任务 7：分类（GET /categories）

**文件：**
- 创建：\`service/CategoryService.java\`、\`controller/CategoryController.java\`、\`dto/response/CategoryResponse.java\`（id String、name、slug、children 递归、sortOrder）
- 测试：\`service/CategoryServiceTest.java\`（可见分类树排序、隐藏分类过滤）、\`controller/CategoryControllerTest.java\`（匿名可达）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（查询 status=VISIBLE 全量，内存组树按 sort_order）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 课程分类查询\`

## 任务 8：课程创建与草稿管理

**文件：**
- 创建：\`service/CourseService.java\`、\`service/CourseVersionService.java\`、\`support/TeacherAccessGuard.java\`（归属校验）
- 创建：\`controller/CourseTeacherController.java\`、\`dto/request/CourseCreateRequest.java\`、\`dto/request/CourseDraftUpdateRequest.java\`（全量：title/subtitle/description/coverFileId/level/price/currency/categoryId）、\`dto/response/CourseDraftResponse.java\`
- 测试：\`service/CourseDraftServiceTest.java\`、\`support/TeacherAccessGuardTest.java\`

- [ ] **步骤 1：写失败测试**：POST /courses 建根+首版 DRAFT；PUT 只允许 DRAFT（VERSION_NOT_DRAFT）；跨教师访问草稿 403；POST drafts 从 PUBLISHED/REJECTED 复制新草稿并 version_no+1
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（建课时事务插入 course（owner=当前教师）+ course_teacher(OWNER) + course_version(DRAFT)；PUT 校验归属 + version_status=DRAFT + 乐观锁；封面 bind 见任务 12）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 课程创建与草稿管理\`

## 任务 9：审核状态机（提交/审批/驳回/撤回 + 原子发布）

**文件：**
- 创建：\`state/CourseVersionStateMachine.java\`（DRAFT→PENDING_REVIEW→REJECTED/PUBLISHED→SUPERSEDED/WITHDRAWN 转移表）
- 创建：\`state/AuditStateMachine.java\`（PENDING→APPROVED/REJECTED/WITHDRAWN）
- 创建：\`service/CourseAuditService.java\`、\`controller/CourseAuditController.java\`、\`dto/response/CourseAuditResponse.java\`、\`dto/request/AuditRejectRequest.java\`
- 测试：\`state/CourseVersionStateMachineTest.java\`、\`service/CourseAuditServiceTest.java\`（自审拒绝 403、非法转移 409）、\`service/CourseAuditPublishIT.java\`（Testcontainer：提交→审批同事务发布 + 旧版 SUPERSEDED + outbox 落库）

- [ ] **步骤 1：写失败测试**（状态机全转移 + 非法转移；approve 同事务断言 course.published_version_id 切换）
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（submit：校验归属+DRAFT→PENDING_REVIEW+写 submission；approve：SELECT course FOR UPDATE→submission APPROVED→published_version_id 切换→旧版 SUPERSEDED→lifecycle=PUBLISHED→outbox CoursePublished→同事务提交；reject 原因必填；withdraw 仅提交教师+PENDING）
- [ ] **步骤 4：运行确认通过**（含 IT）
- [ ] **步骤 5：Commit**：\`feat(course): 课程审核状态机与原子发布\`

## 任务 10：生命周期（下架/重上架/归档）

**文件：**
- 创建：\`controller/CourseLifecycleController.java\`；\`service/CourseService.java\` 增加 offline/republish/archive
- 测试：\`service/CourseLifecycleServiceTest.java\`

- [ ] **步骤 1：写失败测试**：offline 仅 PUBLISHED；republish 仅 OFFLINE 且有 published_version_id（M05 就绪 gate 恒放行）；archive 仅 OFFLINE（PUBLISHED 直接 archive → 409）、归档后不可 republish；权限校验
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（锁根 + 状态迁移 + outbox CourseOfflined/CourseRepublished/CourseArchived）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 课程生命周期操作\`

## 任务 11：公开列表与详情（含可见性与分页）

**文件：**
- 创建：\`service/CourseCatalogService.java\`、\`controller/CourseCatalogController.java\`、\`dto/response/CourseSummaryResponse.java\`、\`dto/response/CourseDetailResponse.java\`、\`dto/request/CourseListQuery.java\`
- 测试：\`service/CourseCatalogServiceTest.java\`（过滤/排序白名单/分页/已选标记/enrolled）、\`controller/CourseCatalogControllerTest.java\`（匿名可读、教师本人见草稿态、他人 404）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（SQL 分页 + categoryId/level/priceRange/keyword 过滤 + 排序白名单映射；detail 按可见性：PUBLISHED 公开 / 归属教师看 draft/pending；review 列表分页）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 课程公开列表与详情\`

## 任务 12：FileClient 与封面集成（bind/grant）

**文件：**
- 创建：\`service/FileClient.java\`（复刻 user 版：服务令牌缓存提前 30s、批量≤100、403→COURSE_ACCESS_DENIED/404→COURSE_NOT_FOUND 语义映射、enabled=false no-op）、\`config/CourseFileProperties.java\`
- 修改：\`educloud-backend/educloud-file/src/main/java/.../support/OwnerServiceRegistry.java\`（或配置）注册 \`educloud-course\` → ownerService=course
- 测试：\`service/FileClientTest.java\`（MockRestServiceServer：bind 成功/403/404/503）、\`service/CourseCoverBindTest.java\`（上传者属主校验：他人 fileId bind 403）、\`CourseCatalogServiceTest.java\` 扩展（列表一次批量 grant、匿名 PUBLIC_CATALOG、伪造 courseId 不签封面）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现 FileClient + 注册**（File 侧 OwnerServiceRegistry 加 course clientId；File 现有 IT 全量回归）
- [ ] **步骤 4：运行确认通过**（course + file 模块测试全绿）
- [ ] **步骤 5：Commit**：\`feat(course): 封面 File 集成（bind/批量 grant）\`

## 任务 13：选课（幂等免费选课/我的课程/学生列表）

**文件：**
- 创建：\`service/EnrollmentService.java\`、\`controller/EnrollmentController.java\`、\`dto/response/EnrollmentResponse.java\`、\`dto/response/MyCourseResponse.java\`、\`dto/response/CourseStudentResponse.java\`
- 测试：\`service/EnrollmentServiceTest.java\`（幂等：重复选课返回现状；付费 409；OFFLINE/ARCHIVED 409；enrollment_count 递增）、\`service/EnrollmentConcurrencyIT.java\`（Testcontainer 并发双请求单行）、\`service/EnrollmentAccessTest.java\`（学生查他人课程学生列表 403、教师归属通过）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（选课：锁根→校验 PUBLISHED+免费→幂等 INSERT IGNORE/已存在返回→count 递增→outbox EnrollmentCreated；我的课程：enrollment JOIN course JOIN published version；学生列表：归属校验 + 分页）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 免费选课/我的课程/学生列表\`

## 任务 14：评价（upsert/隐藏/汇总重算）

**文件：**
- 创建：\`service/CourseReviewService.java\`、\`controller/CourseReviewController.java\`、\`dto/request/ReviewUpsertRequest.java\`、\`dto/response/CourseReviewResponse.java\`
- 测试：\`service/CourseReviewServiceTest.java\`（未选课 403、rating 越界 400、upsert 幂等、HIDDEN 不展示、管理角色隐藏后重算 avg/count）、\`service/ReviewSummaryIT.java\`（Testcontainer 汇总一致性）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（upsert：校验 ACTIVE 选课→INSERT ON DUPLICATE→同事务重算 rating_avg/rating_count；DELETE /course-reviews/{id}：管理角色→HIDDEN→重算）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 课程评价与评分汇总\`

## 任务 15：事件与 Outbox

**文件：**
- 创建：\`messaging/CourseEventPublisher.java\`（CoursePublished/CourseOfflined/CourseRepublished/CourseArchived/EnrollmentCreated/EnrollmentRevoked，EventEnvelope 复用 common；routing key \`Course.{id}\`/\`Enrollment.{id}\`）
- 修改：任务 9/10/13 已接入发布点；本任务补全 + 配置 RabbitConfig（TopicExchange educloud.events、vhost=educloud）
- 测试：\`messaging/CourseEventPublisherTest.java\`（Outbox 落库、信封字段）、\`messaging/OutboxDispatchIT.java\`（真实 Rabbit Testcontainer 分发）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（复用 common OutboxWriter；分发线程 + 退避 + FAILED 阈值）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 课程/选课事件与 Outbox 分发\`

**备注（规格审查通过后记录，2026-08-23）：**
- EnrollmentRevoked 按规格 §8 为 M07 预留：M05 无触发路径，不实现死代码方法（CourseEventPublisher 仅 4 个生命周期方法 + EnrollmentCreated，共 5 个）。
- OutboxDispatchIT 仅覆盖 \`Course.#\` 通配绑定接收验证（\`Enrollment.#\` 可选补）。
- vhost=educloud 由 VM e2e 覆盖验证（本机单元测试不连真实 RabbitMQ；application.yml 已默认 \`RABBITMQ_DEFAULT_VHOST:educloud\`）。
- Outbox 分发为 at-least-once（可靠性设计 4.1.4，消费者幂等）：当前条件更新只防状态覆盖，多实例去重（FOR UPDATE SKIP LOCKED / 租约）在 M06+ 引入消费者后按需评估。

## 任务 16：可观测性（readiness/业务指标/审计）

**文件：**
- 创建：`observability/HealthIndicatorConfig.java`（管理端口 readiness 组：mysql/redis/rabbit/nacos，复刻 user/file 模式）、`observability/CourseMetrics.java`（Micrometer 计数：course_published、enrollment_created、audit_approved/rejected）、`observability/AuditWriter.java`（关键写操作写 `audit_event`，actor_type 存角色名或 USER）
- 修改：`application.yml`（management.endpoint.health.group.readiness 依赖项）
- 测试：`observability/CourseMetricsTest.java`、`observability/AuditWriterTest.java`

- [ ] **步骤 1：写失败测试**（指标计数递增、审计行写入与 actor_type 长度兼容 VARCHAR(32)）
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（复用 M04 模式：CompositeHealthIndicator + Micrometer counter + audit_event 写库）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：`feat(course): 可观测性（readiness/业务指标/审计）`

## 任务 17：内部接口（/internal/v1/courses/{id}）


**文件：**
- 创建：\`controller/InternalCourseController.java\`、\`dto/response/InternalCourseAccessResponse.java\`（courseId、lifecycleStatus、publishedVersionId、draftVersionId、ownerTeacherId、contentReady(恒 false 占位)、teachers）
- 测试：\`controller/InternalCourseControllerTest.java\`（无服务令牌 401、未知 clientId 403、正常响应）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（InternalApiFilter 校验 aud=educloud-course + clientId 白名单；返回可见性/归属快照，供 M06 content 消费）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 内部课程访问接口\`

## 任务 18：种子数据 V002

**文件：**
- 创建：\`deploy/sql/course/V002__seed.sql\`（幂等：分类树 3 顶级+6 子分类；6 门已发布课程 + demo_teacher 名下 DRAFT 1 门 + PENDING_REVIEW 1 门；course_teacher 归属；选课 2 条（fe_demo_10）；评价 2 条；enrollment_count/rating 与种子一致；cover_file_id 全 NULL）
- 测试：\`CourseSeedIT.java\`（Testcontainer 执行 V002 断言数量与幂等可重放）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：编写种子 SQL**（Snowflake 用固定可读 ID 如 9000000000000000100+ 序列，避免与运行时 ID 冲突；slug 唯一）
- [ ] **步骤 4：运行确认通过**
- [ ] **步骤 5：Commit**：\`feat(course): 演示种子数据（分类/课程/选课/评价）\`

## 任务 19：部署脚本与 VM 环境

**文件：**
- 创建：\`deploy/scripts/provision-course-nacos.sh\`（复制 provision-file-nacos.sh：账号 \`educloud_course\`、权限 \`naming/educloud-course\`）
- 修改：\`deploy/docker-compose/.env.example\`（EDUCLOUD_COURSE_DB_PASSWORD、EDUCLOUD_COURSE_MIGRATION_PASSWORD、NACOS_COURSE_USERNAME/PASSWORD、EDUCLOUD_COURSE_FILE_CLIENT_ID/SECRET/ENDPOINT）
- 修改：\`deploy/scripts/start-dev.sh\`（新增 \`[4/6] educloud-course\` 段：SERVER_PORT=8089 COURSE_MANAGEMENT_PORT=8090、MYSQL/REDIS/RABBIT/NACOS env、COURSE_JWKS_LOCATION、EDUCLOUD_COURSE_FILE_*、\`wait_ready http://127.0.0.1:8090/actuator/health/readiness\`、末尾打印 Course 信息）
- 修改：\`deploy/scripts/bootstrap-service-clients.sh\` 用法注释（示例加 course）
- 验证：\`deploy/scripts/run-migrations.sh --service course\` 通用支持

- [ ] **步骤 1：本地验证**：\`mvn -pl educloud-course -am package -DskipTests\` 成功
- [ ] **步骤 2：Commit**（\`chore(deploy): educloud-course 部署脚本与环境变量\`）

## 任务 20：VM 端到端验证（迁移/拉起/链路/越权）

**文件：** 无（执行验证）

- [ ] **步骤 1：同步**：tar（排除 .git/target/node_modules/dist/secrets/.env）→ ssh_upload → 解压覆盖
- [ ] **步骤 2：迁移**：\`MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 EDUCLOUD_COURSE_MIGRATION_PASSWORD=… bash deploy/scripts/run-migrations.sh --service course\` + user 库 V004
- [ ] **步骤 3：Nacos provision**：\`bash deploy/scripts/provision-course-nacos.sh --env-file deploy/docker-compose/.env\`
- [ ] **步骤 4：bootstrap**：\`CLIENT_ID=educloud-course AUDIENCES='["educloud-file"]' SCOPES='["file:internal"]' BOOTSTRAP_KEY=… printf '%s' "$SECRET" | bash deploy/scripts/bootstrap-service-clients.sh\`
- [ ] **步骤 5：构建**：\`/opt/maven/bin/mvn -pl educloud-course -am package -DskipTests\`（VM JDK17）+ 前端 \`tsc && vite build\`
- [ ] **步骤 6：启动**：\`bash deploy/scripts/start-dev.sh\`（course 8089/8090 监听；gateway 日志确认 course-core 路由可达）
- [ ] **步骤 7：链路验证**（curl/python 走网关）：登录 demo_teacher → 建课 → 编辑草稿 → 提交 → demo_admin 审批 → 学生 fe_demo_10 列表可见新课程；免费选课幂等（重复 200）；未选课学生写评价 403；匿名 GET /courses 无封面泄露；越权：教师 A 读教师 B 草稿 403、学生查他人学生列表 403、伪造 coverFileId bind 403
- [ ] **步骤 8：浏览器验证**：学生端列表/详情/选课/我的课程/评价；教师端建课→上传封面→提交；管理端审批；三门户状态一致
- [ ] **步骤 9：向用户汇报并等待确认**（契约门禁 9）

## 任务 21：前端 student 联调

**文件：**
- 创建：\`educloud-frontend/student-portal/src/services/courseApi.ts\`（真实 API：categories/courses/course detail/enrollments/me-enrollments/reviews；Snowflake 全 string）
- 修改：\`educloud-frontend/student-portal/src/services/api.ts\`（courseApi mock 替换/移除，保留非课程 mock）、\`src/types/index.ts\`（Course/Category/Review/PaginatedResponse 对齐真实 DTO）、\`src/stores/useCourseStore.ts\`（接 courseApi）、\`src/pages/CourseList.tsx\`、\`src/pages/CourseDetail.tsx\`（章节区「目录即将上线」占位 + 评价区）、\`src/pages/MyCourses.tsx\`（真实选课列表 + 免费选课按钮/付费提示）、相关组件 CourseCard/CourseSortSelect（字段名对齐）
- 测试：\`tsc --noEmit\` + \`vite build\` + 浏览器冒烟

- [ ] **步骤 1：typecheck 基线**（\`npx tsc --noEmit\` 通过）
- [ ] **步骤 2：实现 courseApi.ts + types 对齐**
- [ ] **步骤 3：替换 CourseList/CourseDetail/MyCourses 数据源**（无 mock 回退；加载/空/错误 UI 保留）
- [ ] **步骤 4：浏览器冒烟**（VM：登录→列表→详情→选课→我的课程→评价）
- [ ] **步骤 5：Commit**（\`feat(student): 课程模块真实 API 联调\`）

## 任务 22：前端 teacher 联调

**文件：**
- 修改：\`educloud-frontend/teacher-portal/src/services/api.ts\`（或新增 courseTeacherApi.ts：课程列表/草稿/保存/提交审核/学生列表/封面上传）、\`src/pages/CourseManage.tsx\`（状态列表）、\`src/pages/CourseEdit.tsx\`（表单真实保存 + uploadCover 复用 file.ts 模式 + 提交审核）、\`src/pages/StudentList.tsx\`、types 对齐
- 测试：\`tsc --noEmit\` + \`vite build\` + 浏览器冒烟

> **执行记录（任务 22 已完成，commit 4dd54c4）：计划外后端补充 —— 规格 §6 原无教师课程列表端点，`GET /api/v1/courses` 仅公开已发布列表；本任务按需补齐 `GET /api/v1/teacher/courses`（`course:update` + 归属，COALESCE(draft,published,最新版本) 驱动，封面 USER grant），并同步网关路由（course-core Path + RouteGroups）。前端以新增 `services/teacherCourseApi.ts` 接入（无 mock 回退），保留其余模块 mock 不动。

- [x] **步骤 1：typecheck 基线**
- [x] **步骤 2：实现 teacher 课程 API 层**
- [x] **步骤 3：替换 CourseManage/CourseEdit/StudentList**
- [ ] **步骤 4：浏览器冒烟**（建课→填表→封面上传→保存草稿→提交审核；学生列表；留待最终 Playwright 统一执行）
- [x] **步骤 5：Commit**（\`feat(teacher): 课程管理与封面上传真实联调\`）

## 任务 23：前端 admin 联调

**文件：**
- 修改：\`educloud-frontend/admin-portal/src/services/api.ts\`（或新增 courseAdminApi.ts：待审列表/详情/批准/驳回）、\`src/pages/CourseAudit.tsx\`（真实审核 + 原因必填）、上下架/归档入口（可并入 CourseAudit 或独立页）、types 对齐
- 测试：\`tsc --noEmit\` + \`vite build\` + 浏览器冒烟

- [ ] **步骤 1：typecheck 基线**
- [ ] **步骤 2：实现 admin 课程 API 层**
- [ ] **步骤 3：替换 CourseAudit + 生命周期操作**
- [ ] **步骤 4：浏览器冒烟**（教师提交→admin 批准→学生端出现；驳回原因必填）
- [ ] **步骤 5：Commit**（\`feat(admin): 课程审核与生命周期管理真实联调\`）

## 任务 24：全量门禁与独立代码审查

**文件：** 无（验证 + 文档勾选）

- [ ] **步骤 1：全量**：\`mvn -pl educloud-common,educloud-gateway,educloud-user,educloud-file,educloud-course -am verify\`（-Pintegration 跑 IT）BUILD SUCCESS
- [ ] **步骤 2：规格审查**：对照 2026-08-23-educloud-course-design.md 逐节核对实现与测试覆盖
- [ ] **步骤 3：独立代码审查**：按 chinese-code-review 覆盖六维度；重点：越权（跨教师草稿/学生列表/伪造封面/自审）、状态机非法转移、选课幂等与并发、评价范围、审核原子性、事件版本一致性；自动修复可确定项，其余列待确认
- [ ] **步骤 4：修复验证 + 汇报**（含 BUG 风险表）→ 等待用户确认后进入 M06

---

## 自检记录（编写时已核对）

- 规格覆盖：分类→任务 7；课程根/草稿→任务 8；审核状态机→任务 9；生命周期→任务 10；公开列表/详情→任务 11；封面集成→任务 12；选课/学生列表→任务 13；评价→任务 14；事件→任务 15；可观测性→任务 16；内部接口→任务 17；RBAC→任务 3；种子→任务 18；配置→任务 0/19；前端三阶段→任务 21/22/23；门禁→任务 20/24。
- 类型一致性：\`CourseDraftResponse\` 在任务 8 定义并被任务 20/21 引用；\`CourseSummaryResponse\` 在任务 11 定义并被任务 20 引用；\`FileClient\` 在任务 12 定义并被任务 8（bind）与任务 11（grant）使用；\`CourseEventPublisher\` 在任务 15 定义并被任务 9/10/13 调用（发布点在各自任务先以本地方法形式接入，任务 15 补全实现）。
- 无占位符：所有任务含具体文件、命令与测试断言。
- 越权门禁用例在任务 19 步骤 7 全覆盖规格 §9 清单。
