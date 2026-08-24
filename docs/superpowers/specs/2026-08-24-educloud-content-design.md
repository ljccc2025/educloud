# EduCloud M06 内容模块设计规格说明书（educloud-content）

> 日期：2026-08-24
> 
> 状态：已评审设计基线
> 
> 范围：M06 课程内容、不可变修订、章节与课件、文件播放授权、学习进度追踪、三端前后端联调与端到端测试
> 
> 执行原则：严格对齐 12 服务基线规范与交接文档（`交接文档-2026-08-24-M05.md`）

---

## 1. 背景与目标

M05 阶段已完成课程服务（`educloud-course`）的全链路交付（课程分类、CRUD、版本审核发布状态机、选课、评价、封面集成与三端联调）。
当前前端课程大纲展示的是 `description` 占位，教师端「管理内容」未接通真实服务，学生端 `/learn/{id}` 仍为静态 Mock 数据。

M06 旨在建设权威的内容服务（`educloud-content`），彻底打通核心教学与学习全闭环：
1. **课程内容不可变修订流**：支持教师在草稿中构建章节课件树，提审后由管理员审核并原子发布为正式版本。
2. **安全课件媒体直传与播放授权**：与 File 服务（MinIO）深度集成，提供视频、PDF 文档及外链的绑定与短期预签名播放授权。
3. **真实防作弊学习进度**：学员心跳上报增量观看时长，服务端校验并自动聚合计算课程整体完成百分比。
4. **三端全链路打通与自动化验收**：学生端大纲与学习页、教师端内容编辑器、管理端内容审核流完全真实化，并通过 Playwright E2E 全自动化验收。

---

## 2. 总体架构与服务契约

### 2.1 服务属性

- **服务名称**：`educloud-content`
- **业务端口**：`8085` / **Actuator 端口**：`8086`
- **独立逻辑库**：`educloud_content`（禁止跨库直接访问，仅通过 API 与事件通信）
- **网关路由**：全部外部请求由 `educloud-gateway`（8080）统一路由与 JWT Access Token 校验。

### 2.2 领域事件（RabbitMQ Topic: `educloud.events`）

- **发布事件**：
  - `ContentRevisionPublished`（RoutingKey: `Content.Published.{courseId}`）：当内容修订版本审核通过并原子发布时触发，包含 `courseId`, `contentRootId`, `publishedRevisionId`, `aggregateVersion`, `timestamp`。
- **消费事件**：
  - `CoursePublished` / `CourseOffline`：同步感知课程生命周期状态。
  - `EnrollmentCreated` / `EnrollmentRevoked`：感知学员选课状态，更新本地访问投影。

---

## 3. 数据库与数据表设计（`educloud_content`）

### 3.1 核心表结构

#### 1. `course_content`（课程内容根表）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | Snowflake ID |
| `course_id` | BIGINT NOT NULL UNIQUE | 关联的课程 ID |
| `published_revision_id` | BIGINT NULL | 当前正式发布的不可变修订版本 ID |
| `aggregate_version` | BIGINT NOT NULL DEFAULT 1 | 乐观锁与聚合版本号 |
| `created_at` | DATETIME NOT NULL | 创建时间 |
| `updated_at` | DATETIME NOT NULL | 更新时间 |

#### 2. `content_revision`（内容不可变修订版本表）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 修订版本 ID |
| `course_content_id` | BIGINT NOT NULL | 所属内容根 ID |
| `course_id` | BIGINT NOT NULL | 关联课程 ID |
| `revision_no` | INT NOT NULL | 修订版本号（1, 2, 3...） |
| `revision_status` | VARCHAR(32) NOT NULL | `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `SUPERSEDED`, `REJECTED`, `WITHDRAWN` |
| `content_hash` | VARCHAR(64) NULL | 章节课件结构 SHA256 指纹 |
| `created_by` | BIGINT NOT NULL | 创建教师用户 ID |
| `created_at` | DATETIME NOT NULL | 创建时间 |
| `submitted_at` | DATETIME NULL | 提交审核时间 |
| `published_at` | DATETIME NULL | 审核发布时间 |
- **唯一索引**：`uk_course_revision(course_id, revision_no)`

#### 3. `chapter`（章节表）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 章节 ID |
| `content_revision_id` | BIGINT NOT NULL | 所属修订版本 ID |
| `course_id` | BIGINT NOT NULL | 课程 ID |
| `title` | VARCHAR(128) NOT NULL | 章节名称 |
| `description` | VARCHAR(512) NULL | 章节描述 |
| `sort_order` | INT NOT NULL | 排序序号（1, 2, 3...） |
| `status` | VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' | `ACTIVE` / `DELETED` |
| `created_at` | DATETIME NOT NULL | 创建时间 |
| `updated_at` | DATETIME NOT NULL | 更新时间 |
- **唯一索引**：`uk_revision_sort(content_revision_id, sort_order)`

#### 4. `courseware`（课件/课时表）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 课件 ID |
| `content_revision_id` | BIGINT NOT NULL | 所属修订版本 ID |
| `chapter_id` | BIGINT NOT NULL | 所属章节 ID |
| `course_id` | BIGINT NOT NULL | 课程 ID |
| `title` | VARCHAR(128) NOT NULL | 课件名称 |
| `courseware_type` | VARCHAR(32) NOT NULL | `VIDEO`, `DOCUMENT`, `PPT`, `EXTERNAL_URL` |
| `file_id` | BIGINT NULL | 关联的 File 服务文件对象 ID（与 external_url 互斥） |
| `external_url` | VARCHAR(1024) NULL | 外部媒体链接 |
| `duration_seconds` | INT NOT NULL DEFAULT 0 | 视频/音频时长（秒） |
| `size_bytes` | BIGINT NOT NULL DEFAULT 0 | 资源大小（字节） |
| `free_preview` | TINYINT(1) NOT NULL DEFAULT 0 | 是否支持免选课试看（`1=是`, `0=否`） |
| `sort_order` | INT NOT NULL | 章节内排序序号 |
| `status` | VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' | `ACTIVE` / `DELETED` |
| `created_at` | DATETIME NOT NULL | 创建时间 |
| `updated_at` | DATETIME NOT NULL | 更新时间 |
- **唯一索引**：`uk_chapter_sort(chapter_id, sort_order)`

#### 5. `user_courseware_progress`（学员单课时学习进度表）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 记录 ID |
| `student_id` | BIGINT NOT NULL | 学员用户 ID |
| `course_id` | BIGINT NOT NULL | 课程 ID |
| `courseware_id` | BIGINT NOT NULL | 课件 ID |
| `position_seconds` | INT NOT NULL DEFAULT 0 | 当前播放进度位置（秒） |
| `watched_seconds` | INT NOT NULL DEFAULT 0 | 累计有效观看时长（秒） |
| `completed` | TINYINT(1) NOT NULL DEFAULT 0 | 课时是否已学完（`1=是`, `0=否`） |
| `completed_at` | DATETIME NULL | 完成时间 |
| `last_learned_at` | DATETIME NOT NULL | 最近学习心跳时间 |
- **唯一索引**：`uk_student_cw(student_id, courseware_id)`

#### 6. `user_course_progress`（学员课程整体进度聚合表）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 记录 ID |
| `student_id` | BIGINT NOT NULL | 学员用户 ID |
| `course_id` | BIGINT NOT NULL | 课程 ID |
| `completed_courseware_count` | INT NOT NULL DEFAULT 0 | 已完成课时数 |
| `total_courseware_count` | INT NOT NULL DEFAULT 0 | 课程总课时数 |
| `progress_percent` | INT NOT NULL DEFAULT 0 | 进度百分比（0 ~ 100） |
| `last_learned_courseware_id` | BIGINT NULL | 最近一次学习的课件 ID |
| `updated_at` | DATETIME NOT NULL | 更新时间 |
- **唯一索引**：`uk_student_course(student_id, course_id)`

#### 7. `content_audit_submission`（内容审核申请单表）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 审核单 ID |
| `course_id` | BIGINT NOT NULL | 关联课程 ID |
| `content_revision_id` | BIGINT NOT NULL | 提审的修订版本 ID |
| `revision_no` | INT NOT NULL | 修订版本号 |
| `snapshot_json` | LONGTEXT NOT NULL | 提审时冻结的章节与课件目录快照 JSON |
| `status` | VARCHAR(32) NOT NULL DEFAULT 'PENDING' | `PENDING`, `APPROVED`, `REJECTED`, `WITHDRAWN` |
| `submitted_by` | BIGINT NOT NULL | 提审教师 ID |
| `reviewed_by` | BIGINT NULL | 审批管理员 ID |
| `reject_reason` | VARCHAR(512) NULL | 驳回原因 |
| `submitted_at` | DATETIME NOT NULL | 提审时间 |
| `reviewed_at` | DATETIME NULL | 审批时间 |
| `withdrawn_at` | DATETIME NULL | 撤回时间 |

---

## 4. API 契约设计

### 4.1 学生端接口

- **`GET /api/v1/courses/{courseId}/chapters`**
  - **权限**：公开（匿名或登录均可）
  - **说明**：获取课程当前已发布修订版本的章节课件树。若请求带学员 Token，附加每个课件的个人完成状态（`completed`）。
- **`GET /api/v1/coursewares/{id}/download-url`**
  - **权限**：需登录
  - **说明**：课件播放/下载授权。校验：调用者是课程教师 OR 学员具有有效选课 OR `courseware.free_preview=1`。校验通过后，Content 使用服务 Token 调用 File 服务申请 15 分钟临时 MinIO Presigned URL 返回给前端。
- **`PUT /api/v1/coursewares/{id}/progress`**
  - **权限**：学员（需有有效选课）
  - **入参**：`{ "positionSeconds": 120, "watchedDeltaSeconds": 15, "completed": false }`
  - **说明**：增量上报播放心跳，更新 `user_courseware_progress` 并自动重算 `user_course_progress`。
- **`GET /api/v1/me/courses/{courseId}/progress`**
  - **权限**：登录学员
  - **说明**：返回该学员在该课程的进度详情与已完成课时列表。
- **`GET /api/v1/me/course-progress?courseIds=1,2,3`**
  - **权限**：登录学员
  - **说明**：批量返回指定课程的学习进度列表，用于“我的课程”页面高效渲染。

### 4.2 教师端接口

- **`GET /api/v1/teacher/courses/{courseId}/content-draft`**
  - **权限**：课程主讲教师
  - **说明**：获取当前课程的可编辑草稿版本树；若不存在草稿，则自动基于当前已发布版本或空版本创建新草稿版本。
- **`POST /api/v1/courses/{courseId}/chapters`**
  - **权限**：课程主讲教师
  - **入参**：`{ "title": "第一章 架构基础", "description": "...", "sortOrder": 1 }`
- **`PUT /api/v1/chapters/{id}`** / **`DELETE /api/v1/chapters/{id}`**
  - **权限**：课程主讲教师（仅草稿态可操作）
- **`POST /api/v1/chapters/{chapterId}/coursewares`**
  - **权限**：课程主讲教师
  - **入参**：`{ "title": "1.1 容器化概览", "coursewareType": "VIDEO", "fileId": "209...", "durationSeconds": 600, "freePreview": true, "sortOrder": 1 }`
- **`PUT /api/v1/coursewares/{id}`** / **`DELETE /api/v1/coursewares/{id}`**
  - **权限**：课程主讲教师（仅草稿态可操作）
- **`POST /api/v1/content-revisions/{revisionId}/submit-review`**
  - **权限**：课程主讲教师
  - **说明**：将草稿修订锁定为 `PENDING_REVIEW`，生成冻结快照写入 `content_audit_submission`。

### 4.3 管理端接口

- **`GET /api/v1/content-audits`**：分页查询待审内容单列表。
- **`GET /api/v1/content-audits/{id}`**：查询待审单详情与快照数据。
- **`POST /api/v1/content-audits/{id}/approve`**：审核通过，原子发布修订为 `PUBLISHED`，发出 `ContentRevisionPublished` 事件。
- **`POST /api/v1/content-audits/{id}/reject`**：驳回审核，写入驳回原因。

### 4.4 内部服务间接口（`/internal/v1`）

- **`GET /internal/v1/course-content/{courseId}/readiness-snapshot`**：供 Course 模块查询课程内容发布就绪状态（`ready=true/false`）。

---

## 5. 三端前端集成与改造点

1. **教师端**：
   - 课程列表与编辑页「管理内容」按钮添加导航链接，直达 `/content?courseId={id}`。
   - `ContentManage.tsx` / `ContentEditor.tsx` 对接后端真实 Draft 接口，支持章节课件增删改、MinIO 视频/文档上传直传、及「提交审核」操作。
2. **学生端**：
   - `CourseDetail.tsx` 大纲 Tab 替换 description 占位，调用 `GET /api/v1/courses/{id}/chapters` 渲染真实大纲。
   - `Learning.tsx` 动态加载已发布课件树，点击课件获取授权播放地址，集成心跳进度上报与已完成高亮标记。
   - `MyCourses.tsx` 批量调用 `/api/v1/me/course-progress` 渲染真实进度条。
3. **管理端**：
   - `ContentAudit.tsx` 对接真实待审列表与审核通过/驳回动作。

---

## 6. 测试与验收门禁

1. **数据库迁移与种子数据**：
   - `deploy/sql/content/V000`, `V001`, `V002` + `deploy/sql/user/V006` 全部通过校验。
   - 为现有 8 门经典课及「k8s的实战」预置真实章节与多课时数据，开箱即用。
2. **自动化集成测试（Testcontainers）**：
   - `ContentSchemaIT` / `ContentSeedIT`
   - `ContentRevisionAuditPublishIT`
   - `CoursewareAuthorizationIT`
   - `LearningProgressIT`
3. **Playwright 端到端（E2E）验收**：
   - 编写并执行 `e2e-m06.py`，完成三端全链路端到端闭环验证。
