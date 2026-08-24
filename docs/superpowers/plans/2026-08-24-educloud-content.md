# EduCloud M06 educloud-content 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 交付 `educloud-content` 内容服务：课程内容根与不可变修订（草稿/提审/发布状态机）、章节与课时课件 CRUD、课件文件/外链绑定、MinIO 播放与下载授权集成、防作弊增量学习进度上报与课程总完成度计算；三端前端（教师端内容编辑、学生端大纲与播放学习页、管理端内容审核）全链路替换 Mock 并通过 Playwright E2E 自动化验收。

**架构：** Spring MVC 微服务（对外 8085 / 管理 8086），对外 API 经 Gateway（Bearer + `content:*` 权限码），内部 API 使用服务令牌（aud=educloud-content / educloud-file）；MyBatis-Plus（分页 + 乐观锁）；内容审核通过与发布在同一本地事务原子切换；课件播放向 File 服务申请短期 Presigned URL；Outbox 发布 `ContentRevisionPublished` 领域事件；Course 服务消费事件更新就绪投影。

**技术栈：** Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.12、MySQL 8.0.36（`educloud_content`）、Redis 7.2、RabbitMQ 3.13、Nacos 2.3.2、MinIO、Testcontainers、Playwright。

**规格：** `docs/superpowers/specs/2026-08-24-educloud-content-design.md`（已批准）｜ 既有规范：`docs/superpowers/specs/2026-08-18-educloud-*`

---

## 文件结构（锁定分解）

```text
educloud-backend/educloud-content/
├── pom.xml                                    # 依赖：common/web/security/oauth2-resource-server/
│                                              # mybatis-plus/redis/rabbit/nacos/actuator/micrometer/testcontainers
└── src/
    ├── main/java/com/educloud/content/
    │   ├── ContentApplication.java            # 启动类（@SpringBootApplication + @MapperScan）
    │   ├── config/
    │   │   ├── MybatisPlusConfig.java         # 分页+乐观锁拦截器
    │   │   ├── SecurityConfig.java            # JWT Resource Server（JWKS 校验 User 公钥）
    │   │   ├── InternalApiFilter.java         # /internal/v1/** 服务令牌校验（aud=educloud-content）
    │   │   ├── ContentFileProperties.java     # educloud.content.file.* 配置
    │   │   ├── OutboxConfig.java              # OutboxWriter/EventDispatcher Bean
    │   │   └── RabbitConfig.java              # TopicExchange educloud.events
    │   ├── controller/
    │   │   ├── ChapterCatalogController.java  # GET /courses/{courseId}/chapters
    │   │   ├── CoursewareController.java      # GET /coursewares/{id}/download-url
    │   │   ├── LearningProgressController.java# PUT /coursewares/{id}/progress, GET /me/courses/{id}/progress, GET /me/course-progress
    │   │   ├── ContentTeacherController.java  # GET /teacher/courses/{id}/content-draft, POST chapters, PUT/DELETE chapters, POST/PUT/DELETE coursewares, POST submit-review, POST withdraw
    │   │   ├── ContentAuditController.java    # GET /content-audits, GET /content-audits/{id}, POST approve, POST reject
    │   │   └── InternalContentController.java # GET /internal/v1/course-content/{courseId}/readiness-snapshot
    │   ├── dto/request/  dto/response/        # Snowflake ID 序列化为 String
    │   ├── entity/                            # 7 表实体（course_content, content_revision, chapter, courseware, user_courseware_progress, user_course_progress, content_audit_submission）
    │   ├── exception/                         # ContentErrorCode、ContentExceptionHandler
    │   ├── mapper/                            # 7 Mapper
    │   ├── messaging/                         # ContentEventPublisher（Outbox 写入）与 CourseEventSubscriber（选课/课程事件感知）
    │   ├── security/                          # JwtSecurityUtils、TeacherAccessGuard、CoursewareAccessGuard
    │   ├── service/
    │   │   ├── CourseContentService.java      # 内容根与草稿树维护
    │   │   ├── ContentRevisionService.java    # 版本克隆/状态流转
    │   │   ├── ChapterService.java            # 章节 CRUD
    │   │   ├── CoursewareService.java         # 课件 CRUD 与授权访问
    │   │   ├── LearningProgressService.java   # 增量心跳与课程进度重算
    │   │   ├── ContentAuditService.java       # 审核通过原子发布/驳回
    │   │   └── FileClient.java                # 服务令牌缓存 + grantDownloadUrl（调 File 内部接口）
    │   └── support/                           # 快照序列化、排序校验、防作弊增量校验
    └── test/java/com/educloud/content/
        ├── ...Test.java                       # 单元测试（Mockito）
        └── ...IT.java                         # Testcontainer MySQL/RabbitMQ 集成测试
deploy/sql/content/V000__technical_tables.sql  # 技术表（Outbox/Inbox/ShedLock，GRANT 给 content_app/content_migration）
deploy/sql/content/V001__init_content_schema.sql # 7 业务表 + 索引 + GRANT
deploy/sql/content/V002__content_seed_data.sql # 8 门经典课 + k8s实战课真实章节课件种子（幂等）
deploy/sql/user/V006__content_permissions.sql  # content 权限码 + 角色分配
deploy/scripts/start-dev.sh                    # 修改：新增 educloud-content 段（8085/8086）
deploy/docker-compose/.env.example             # 修改：新增 CONTENT 相关环境变量
educloud-backend/educloud-file/...             # 修改：OwnerServiceRegistry 注册 educloud-content clientId
educloud-frontend/student-portal/...           # 详情大纲、学习页播放器、我的课程进度联调
educloud-frontend/teacher-portal/...           # 内容管理入口、章节课件树编辑、视频直传、提审联调
educloud-frontend/admin-portal/...             # 内容审核列表与通过/驳回联调
e2e-m06.py                                     # Playwright 三端全链路端到端自动化验收脚本
```

---

## 任务 0：模块骨架与构建配置

**文件：**
- 修改：`educloud-backend/pom.xml`（modules 增加 educloud-content）
- 创建：`educloud-backend/educloud-content/pom.xml`
- 创建：`educloud-backend/educloud-content/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/ContentApplication.java`

- [ ] **步骤 1：创建 `educloud-content/pom.xml` 并纳入父工程**
- [ ] **步骤 2：创建配置文件与基础启动类**
- [ ] **步骤 3：运行 `mvn clean compile -pl educloud-content -am` 验证构建通过**
- [ ] **步骤 4：Commit**
```bash
git add educloud-backend/pom.xml educloud-backend/educloud-content/
git commit -m "feat(content): 初始化 educloud-content 模块骨架"
```

---

## 任务 1：数据库迁移脚本与权限配置

**文件：**
- 创建：`deploy/sql/content/V000__technical_tables.sql`
- 创建：`deploy/sql/content/V001__init_content_schema.sql`
- 创建：`deploy/sql/content/V002__content_seed_data.sql`
- 创建：`deploy/sql/user/V006__content_permissions.sql`

- [ ] **步骤 1：编写技术表 V000（Outbox、Inbox、ShedLock）及权限授权**
- [ ] **步骤 2：编写业务表 V001（7 核心业务表与索引）及应用账号权限**
- [ ] **步骤 3：编写种子数据 V002（为现存课程插入真实章节课件与已学进度）**
- [ ] **步骤 4：编写用户权限 V006（`content:manage`, `content:audit` 并挂载至对应角色）**
- [ ] **步骤 5：编写 Schema 迁移测试 `ContentSchemaIT.java` 验证迁移通过**
- [ ] **步骤 6：Commit**
```bash
git add deploy/sql/content/ deploy/sql/user/V006__content_permissions.sql
git commit -m "feat(content): 添加内容服务数据库迁移脚本与种子数据"
```

---

## 任务 2：核心实体、DTO、异常与 Mapper

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/*.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/*.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/**/*.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/exception/ContentErrorCode.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/exception/ContentExceptionHandler.java`

- [ ] **步骤 1：定义 7 大实体类（带 MyBatis-Plus 注解、Snowflake ID 序列化器）**
- [ ] **步骤 2：定义请求 DTO（ChapterCreateRequest, CoursewareCreateRequest, ProgressReportRequest 等）与响应 DTO**
- [ ] **步骤 3：定义错误码与全局异常处理器（涵盖 NOT_FOUND, FORBIDDEN, REVISION_LOCKED, INVALID_PROGRESS 等）**
- [ ] **步骤 4：编写 7 个 Mapper 接口与 XML**
- [ ] **步骤 5：编写 Mapper 集成测试 `ContentMapperIT.java` 验证 CRUD**
- [ ] **步骤 6：Commit**
```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/ educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/ educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/ educloud-backend/educloud-content/src/main/java/com/educloud/content/exception/
git commit -m "feat(content): 核心实体、DTO、异常体系与 Mapper"
```

---

## 任务 3：教师端内容草稿与版本服务（Draft & Revision Lifecycle）

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/CourseContentService.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ChapterService.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/CoursewareService.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/security/TeacherAccessGuard.java`

- [ ] **步骤 1：编写教师访问归属守卫（校验当前登录教师是否为主讲教师）**
- [ ] **步骤 2：实现草稿版本自动初始化与克隆创建（`getOrCreateDraft`, `cloneNewDraft`）**
- [ ] **步骤 3：实现章节的新增、重命名、排序调整与级联删除**
- [ ] **步骤 4：实现课件的新增、编辑（fileId/externalUrl/freePreview）、排序与删除**
- [ ] **步骤 5：编写提审与撤回逻辑（草稿锁定为 `PENDING_REVIEW` 并生成快照）**
- [ ] **步骤 6：编写单元测试 `CourseContentServiceTest.java` 验证版本状态机**
- [ ] **步骤 7：Commit**
```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ educloud-backend/educloud-content/src/main/java/com/educloud/content/security/
git commit -m "feat(content): 实现教师端内容草稿树编辑与提审服务"
```

---

## 任务 4：管理端内容审核与原子发布服务（Audit & Atomic Publishing）

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ContentAuditService.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/ContentEventPublisher.java`

- [ ] **步骤 1：实现待审单分页列表与快照详情查询**
- [ ] **步骤 2：实现审批通过（同一本地事务内将新版本置为 `PUBLISHED`，旧版置为 `SUPERSEDED`，更新内容根 `published_revision_id`）**
- [ ] **步骤 3：实现审批驳回（记录 `reject_reason`，版本置为 `REJECTED`）**
- [ ] **步骤 4：集成 Outbox 机制发布 `ContentRevisionPublished` 领域事件**
- [ ] **步骤 5：编写审核发布集成测试 `ContentRevisionAuditPublishIT.java`**
- [ ] **步骤 6：Commit**
```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ContentAuditService.java educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/
git commit -m "feat(content): 实现内容审核、原子发布与 Outbox 领域事件"
```

---

## 任务 5：课件访问鉴权与 File 服务直传授权集成

**文件：**
- 修改：`educloud-backend/educloud-file/src/main/java/com/educloud/file/security/OwnerServiceRegistry.java`（注册 `educloud-content`）
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/FileClient.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/security/CoursewareAccessGuard.java`

- [ ] **步骤 1：在 File 服务 OwnerServiceRegistry 中注册 `educloud-content` 服务客户端凭据**
- [ ] **步骤 2：编写 `FileClient` 实现服务 Token 获取、缓存与调用 File 内部 `/internal/v1/files/{id}/download-grants`**
- [ ] **步骤 3：编写 `CoursewareAccessGuard` 鉴权逻辑（教师本人 OR 有效选课学员 OR `free_preview=1`）**
- [ ] **步骤 4：实现 `GET /api/v1/coursewares/{id}/download-url` 返回 MinIO 短期临时 URL**
- [ ] **步骤 5：编写鉴权集成测试 `CoursewareAuthorizationIT.java`**
- [ ] **步骤 6：Commit**
```bash
git add educloud-backend/educloud-file/ educloud-backend/educloud-content/src/main/java/com/educloud/content/service/FileClient.java educloud-backend/educloud-content/src/main/java/com/educloud/content/security/CoursewareAccessGuard.java
git commit -m "feat(content): 集成 File 服务 MinIO 播放授权与课件访问鉴权"
```

---

## 任务 6：防作弊学习进度服务与课程完成度聚合

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/LearningProgressService.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/support/ProgressValidator.java`

- [ ] **步骤 1：编写防作弊校验器（校验 `watchedDeltaSeconds <= 心跳周期 + 缓冲`、`positionSeconds <= duration`）**
- [ ] **步骤 2：实现课时增量心跳上报，更新 `user_courseware_progress`**
- [ ] **步骤 3：实现课程总完成度自动重算（计算已完成课时数与百分比，更新 `user_course_progress`）**
- [ ] **步骤 4：实现单课程进度查询与多课程进度批量查询接口**
- [ ] **步骤 5：编写进度测试 `LearningProgressIT.java`**
- [ ] **步骤 6：Commit**
```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/service/LearningProgressService.java educloud-backend/educloud-content/src/main/java/com/educloud/content/support/
git commit -m "feat(content): 实现防作弊增量学习进度记录与课程完成度聚合"
```

---

## 任务 7：Controller、Security 配置与网关路由校验

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/*.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/config/SecurityConfig.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/config/InternalApiFilter.java`

- [ ] **步骤 1：实现公开目录控制器 `ChapterCatalogController`**
- [ ] **步骤 2：实现课件与授权控制器 `CoursewareController`**
- [ ] **步骤 3：实现学习进度控制器 `LearningProgressController`**
- [ ] **步骤 4：实现教师端内容管理控制器 `ContentTeacherController`**
- [ ] **步骤 5：实现管理端内容审核控制器 `ContentAuditController`**
- [ ] **步骤 6：实现内部控制器 `InternalContentController` 与 `InternalApiFilter` 服务令牌保护**
- [ ] **步骤 7：配置 Spring Security 资源服务器（JWKS）与方法级权限**
- [ ] **步骤 8：运行全量单元测试与路由契约测试**
- [ ] **步骤 9：Commit**
```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ educloud-backend/educloud-content/src/main/java/com/educloud/content/config/
git commit -m "feat(content): 控制器层实现、Security 权限与内部服务保护"
```

---

## 任务 8：全量集成测试与门禁验证

**文件：**
- 创建：`educloud-backend/educloud-content/src/test/java/com/educloud/content/it/*.java`

- [ ] **步骤 1：编写并执行 `ContentSchemaIT.java`（Flyway 迁移验证）**
- [ ] **步骤 2：编写并执行 `ContentSeedIT.java`（种子数据完备性断言）**
- [ ] **步骤 3：编写并执行 `ContentRevisionAuditPublishIT.java`（草稿到发布全状态机断言）**
- [ ] **步骤 4：编写并执行 `CoursewareAuthorizationIT.java`（403 拦截与短期授权断言）**
- [ ] **步骤 5：编写并执行 `LearningProgressIT.java`（增量心跳与进度计算断言）**
- [ ] **步骤 6：运行 `mvn clean verify -Pintegration -pl educloud-content -am` 确保全部 PASS**
- [ ] **步骤 7：Commit**
```bash
git add educloud-backend/educloud-content/src/test/
git commit -m "test(content): 完成 educloud-content 全量集成测试套件"
```

---

## 任务 9：教师端前端联调（Teacher Portal）

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/pages/CourseManage.tsx`（管理内容按钮导航）
- 修改：`educloud-frontend/teacher-portal/src/pages/CourseEdit.tsx`（管理内容按钮导航）
- 修改：`educloud-frontend/teacher-portal/src/pages/ContentManage.tsx`（对接真实 Draft 接口）
- 修改：`educloud-frontend/teacher-portal/src/components/ContentEditor.tsx`（章节课件增删改、视频直传与提审）
- 修改：`educloud-frontend/teacher-portal/src/services/api.ts`

- [ ] **步骤 1：在 `api.ts` 中封装 Content 模块教师端 API**
- [ ] **步骤 2：在课程列表与编辑页将「管理内容」按钮连接至 `/content?courseId={id}`**
- [ ] **步骤 3：在 `ContentManage.tsx` 中加载课程的真实内容草稿树**
- [ ] **步骤 4：在 `ContentEditor.tsx` 中实现新增章节、编辑章节、删除章节**
- [ ] **步骤 5：集成 File 三段式直传（上传视频/PDF 到 MinIO $\rightarrow$ 拿 fileId $\rightarrow$ 绑定课件）**
- [ ] **步骤 6：添加「提交审核」按钮与提审状态交互**
- [ ] **步骤 7：Commit**
```bash
git add educloud-frontend/teacher-portal/
git commit -m "feat(teacher-portal): 内容管理真实 API 对接、章节课件编辑与提审流"
```

---

## 任务 10：学生端前端联调（Student Portal）

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/CourseDetail.tsx`（大纲 Tab 替换 description 占位）
- 修改：`educloud-frontend/student-portal/src/pages/Learning.tsx`（真实课件树、视频播放与进度心跳）
- 修改：`educloud-frontend/student-portal/src/pages/MyCourses.tsx`（真实课程总进度条）
- 修改：`educloud-frontend/student-portal/src/services/api.ts`

- [ ] **步骤 1：在 `api.ts` 中封装 Content 模块学生端 API**
- [ ] **步骤 2：改造 `CourseDetail.tsx`，调用 `GET /api/v1/courses/{id}/chapters` 渲染真实章节课件目录**
- [ ] **步骤 3：改造 `Learning.tsx`，动态加载课件列表，点击课件获取 MinIO 授权直链播放**
- [ ] **步骤 4：在 `<VideoPlayer />` 播放过程中按 15 秒心跳上报进度，更新已完成高亮状态**
- [ ] **步骤 5：改造 `MyCourses.tsx`，批量获取并展示真实学习进度百分比**
- [ ] **步骤 6：Commit**
```bash
git add educloud-frontend/student-portal/
git commit -m "feat(student-portal): 课程大纲真实化、学习页播放与心跳进度追踪"
```

---

## 任务 11：管理端前端联调（Admin Portal）

**文件：**
- 修改：`educloud-frontend/admin-portal/src/pages/ContentAudit.tsx`
- 修改：`educloud-frontend/admin-portal/src/components/AuditModal.tsx`
- 修改：`educloud-frontend/admin-portal/src/services/api.ts`

- [ ] **步骤 1：在 `api.ts` 中封装内容审核接口**
- [ ] **步骤 2：在 `ContentAudit.tsx` 中调用 `GET /api/v1/content-audits` 渲染待审列表**
- [ ] **步骤 3：在 `AuditModal.tsx` 中展示章节课件快照树，对接「通过」与「驳回」操作**
- [ ] **步骤 4：Commit**
```bash
git add educloud-frontend/admin-portal/
git commit -m "feat(admin-portal): 内容审核列表与审批/驳回真实联调"
```

---

## 任务 12：环境部署、Nacos 注册与虚拟机运行验证

**文件：**
- 修改：`deploy/scripts/start-dev.sh`（增加 educloud-content 启动段，端口 8085/8086）
- 修改：`deploy/docker-compose/.env.example`

- [ ] **步骤 1：更新 `start-dev.sh` 脚本，加入 `educloud-content` 进程管理**
- [ ] **步骤 2：通过 SSH 同步代码至虚拟机 `/root/educloud/.worktrees/educloud-backend-foundation`**
- [ ] **步骤 3：在虚拟机执行 `mvn clean package` 编译全部微服务**
- [ ] **步骤 4：在虚拟机执行 `bash deploy/scripts/start-dev.sh` 启动所有微服务并确认端口正常**
- [ ] **步骤 5：Commit**
```bash
git add deploy/scripts/start-dev.sh deploy/docker-compose/.env.example
git commit -m "feat(deploy): 纳入 educloud-content 服务启动编排与环境配置"
```

---

## 任务 13：Playwright 端到端自动化验收测试（`e2e-m06.py`）

**文件：**
- 创建：`e2e-m06.py`

- [ ] **步骤 1：编写 `e2e-m06.py` 覆盖以下完整场景：**
  1. 教师登录（`demo_teacher@educloud.cn`） $\rightarrow$ 进入课程内容管理 $\rightarrow$ 为课程创建新章节与课时 $\rightarrow$ 提交内容审核；
  2. 管理员登录（`demo_admin`） $\rightarrow$ 进入内容审核 $\rightarrow$ 查看快照并点击「审核通过」；
  3. 学生登录（`fe_demo_10`） $\rightarrow$ 打开课程详情页 $\rightarrow$ 验证大纲 Tab 实时显示新发布的章节与课件 $\rightarrow$ 选课后进入 `/learn/{id}` 播放视频 $\rightarrow$ 验证心跳上报与进度已完成更新。
- [ ] **步骤 2：在虚拟机/本地运行 `python e2e-m06.py` 验证全流程通过**
- [ ] **步骤 3：Commit**
```bash
git add e2e-m06.py
git commit -m "test(e2e): 添加 M06 三端全链路 Playwright 端到端验收脚本"
```

---

## 任务 14：交接文档与推送远端

**文件：**
- 创建：`交接文档-2026-08-24-M06.md`

- [ ] **步骤 1：整理并编写《交接文档-2026-08-24-M06.md》，记录 M06 交付清单、测试门禁与踩坑要点**
- [ ] **步骤 2：推送变更至 github main 分支（`git push github main`）**
- [ ] **步骤 3：Commit**
```bash
git add 交接文档-2026-08-24-M06.md
git commit -m "docs(M06): 完成 M06 交付交接文档"
```
