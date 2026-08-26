# EduCloud M12 学习分析与指标大屏中心（educloud-analytics）实施计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建 EduCloud M12 学习分析与指标大屏中心微服务（`educloud-analytics`），汇聚全平台各微服务的业务事实与操作审计事件，基于 MySQL 8.0 预聚合日统计模型与 RabbitMQ 实时增量累加，实现教师端教学分析大屏、管理端平台运营大屏、财务营收大屏、全平台审计日志集中检索以及历史指标一键平滑重算引擎，彻底替换双端前端 Mock 并完成全链路 E2E 验证。

**架构：** 基于 Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + RabbitMQ + Redis + MySQL 8.0，统一经 API 网关（8080）对外暴露 REST API（8101 业务 / 8102 监控探针），独立逻辑数据库 `educloud_analytics`。支持基于 `X-User-Id` 的教师租户隔离与 `ROLE_ADMIN`（`analytics:view` / `analytics:rebuild`）管理权限保护。

**技术栈：** Java 17、Spring Boot 3.2.5、Spring Cloud Alibaba Nacos 2023.0.1.0、MyBatis-Plus 3.5.5、JJWT 0.12.5、RabbitMQ 3.13、Redis 7.2、MySQL 8.0.36、React 18、TypeScript 5、Recharts、Playwright / Python E2E。

---

## 任务列表

### 任务 1：数据库迁移脚本、权限配置与 Maven 工程脚手架

**文件：**
- 创建：`deploy/sql/analytics/V000__technical_tables.sql`
- 创建：`deploy/sql/analytics/V001__metrics_and_audit_views.sql`
- 创建：`deploy/sql/user/V012__analytics_permissions.sql`
- 修改：`educloud-backend/pom.xml`
- 创建：`educloud-backend/educloud-analytics/pom.xml`
- 创建：`educloud-backend/educloud-analytics/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/AnalyticsApplication.java`

- [ ] **步骤 1：编写数据库初始化与权限 SQL 脚本**
  - `deploy/sql/analytics/V000__technical_tables.sql`：创建 `analytics_event_inbox` 与 `consumer_watermark` 技术表；
  - `deploy/sql/analytics/V001__metrics_and_audit_views.sql`：创建 `daily_teacher_metrics`、`daily_platform_metrics`、`daily_finance_metrics`、`course_engagement_stats`、`audit_event_read_model` 与 `analytics_rebuild_task` 表；
  - `deploy/sql/user/V012__analytics_permissions.sql`：向 `educloud_user.sys_permission` 插入权限码 171 (`analytics:view`)、172 (`analytics:rebuild`) 并授权给 `ROLE_ADMIN`。

- [ ] **步骤 2：配置 Maven `educloud-analytics/pom.xml` 与父 POM**
  - 父 POM 注册 `<module>educloud-analytics</module>`；
  - 子 POM 引入 `educloud-common`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-oauth2-resource-server`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、`spring-boot-starter-data-redis`、`spring-boot-starter-amqp`、`spring-cloud-starter-alibaba-nacos-discovery` 及测试依赖。

- [ ] **步骤 3：配置 application.yml 与启动类 AnalyticsApplication**
  - 设定业务端口 `8101`、监控端口 `8102`、Nacos 服务名 `educloud-analytics`、MySQL 连接 `educloud_analytics`、RabbitMQ 与 Redis。

- [ ] **步骤 4：运行构建验证**
  - 运行 `mvn -f educloud-backend/educloud-analytics/pom.xml compile`
  - 预期：BUILD SUCCESS。

- [ ] **步骤 5：Commit**
  - `git add deploy/sql/analytics deploy/sql/user/V012__analytics_permissions.sql educloud-backend/pom.xml educloud-backend/educloud-analytics`
  - `git commit -m "feat(analytics): add sql migrations, permissions and maven project scaffold"`

---

### 任务 2：数据持久层实体、Mapper 与数据库读写配置

**文件：**
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/enums/RebuildStatus.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/enums/RebuildStage.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/entity/DailyTeacherMetricsEntity.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/entity/DailyPlatformMetricsEntity.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/entity/DailyFinanceMetricsEntity.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/entity/CourseEngagementStatsEntity.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/entity/AuditEventReadModelEntity.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/entity/AnalyticsRebuildTaskEntity.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/entity/AnalyticsEventInboxEntity.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/mapper/DailyTeacherMetricsMapper.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/mapper/DailyPlatformMetricsMapper.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/mapper/DailyFinanceMetricsMapper.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/mapper/CourseEngagementStatsMapper.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/mapper/AuditEventReadModelMapper.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/mapper/AnalyticsRebuildTaskMapper.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/mapper/AnalyticsEventInboxMapper.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/mapper/DailyMetricsMapperTest.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/mapper/AuditEventReadModelMapperTest.java`

- [ ] **步骤 1：编写 Mapper 单元测试**
  - 测试 `DailyTeacherMetricsMapper` 原子 Upsert 累加；
  - 测试 `AuditEventReadModelMapper` 组合条件（operator, level, keyword, dateRange）分页查询。
- [ ] **步骤 2：实现实体类、枚举与 Mapper 接口**
- [ ] **步骤 3：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-analytics/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 4：Commit**
  - `git add educloud-backend/educloud-analytics`
  - `git commit -m "feat(analytics): implement persistence entities, mappers and atomic upsert statements"`

---

### 任务 3：核心指标日度累加与按月/多维聚合计算服务

**文件：**
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/teacher/TeacherStatsResponse.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/teacher/EnrollmentTrendItem.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/teacher/RevenueTrendItem.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/teacher/EngagementItem.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/teacher/TeacherActivityItem.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/admin/DashboardStatsResponse.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/admin/UserGrowthItem.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/admin/DistributionsResponse.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/admin/FinanceOverviewResponse.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/dto/response/admin/MonthlyFinanceItem.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/DailyAggregationService.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/TeacherAnalyticsService.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/AdminAnalyticsService.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/FinanceAnalyticsService.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/impl/DailyAggregationServiceImpl.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/impl/TeacherAnalyticsServiceImpl.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/impl/AdminAnalyticsServiceImpl.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/impl/FinanceAnalyticsServiceImpl.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/service/TeacherAnalyticsServiceTest.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/service/AdminAnalyticsServiceTest.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/service/FinanceAnalyticsServiceTest.java`

- [ ] **步骤 1：编写业务聚合计算单元测试**
  - 测试教师端近 6 个月报名/收益补零对齐逻辑；
  - 测试管理端双折线趋势计算与环形/饼图百分比换算；
  - 测试财务大屏净营收与退款率计算。
- [ ] **步骤 2：实现 DTO 响应模型与服务类**
- [ ] **步骤 3：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-analytics/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 4：Commit**
  - `git add educloud-backend/educloud-analytics`
  - `git commit -m "feat(analytics): implement teacher, admin and finance aggregation query services"`

---

### 任务 4：RabbitMQ 领域事件驱动与实时增量同步监听器

**文件：**
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/event/UserDomainEvent.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/event/CourseDomainEvent.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/event/PaymentDomainEvent.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/event/ContentDomainEvent.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/event/AuditEvent.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/config/RabbitMqConfig.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/AnalyticsEventConsumer.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/AuditEventConsumer.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/messaging/AnalyticsEventConsumerTest.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/messaging/AuditEventConsumerTest.java`

- [ ] **步骤 1：编写 Consumer 单元测试**
  - 测试各事件（`UserRegistered`, `EnrollmentCreated`, `PaymentSuccess`, `RefundCompleted`, `ProgressUpdated`）正确路由并触发原子累加；
  - 测试 `analytics_event_inbox` 幂等防重机制；
  - 测试操作审计事件自动入库 `audit_event_read_model`。
- [ ] **步骤 2：实现 RabbitMqConfig、事件模型与消费者**
- [ ] **步骤 3：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-analytics/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 4：Commit**
  - `git add educloud-backend/educloud-analytics`
  - `git commit -m "feat(analytics): implement rabbitmq event consumers and real-time incremental aggregation"`

---

### 任务 5：跨库全量数据抽取与指标平滑重算引擎

**文件：**
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/support/CrossDbBatchExtractor.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/AggregationRebuildService.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/impl/AggregationRebuildServiceImpl.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/service/AggregationRebuildServiceTest.java`

- [ ] **步骤 1：编写 Rebuild 单元测试**
  - 测试任务号生成、多数据源分批抽取、历史按日/按月数据内存分桶重算；
  - 测试任务状态流转（`RUNNING` $\to$ `25%` $\to$ `50%` $\to$ `75%` $\to$ `100% SUCCESS`）；
  - 测试异常捕获与 `FAILED` 状态记录。
- [ ] **步骤 2：实现 CrossDbBatchExtractor 与 AggregationRebuildServiceImpl**
- [ ] **步骤 3：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-analytics/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 4：Commit**
  - `git add educloud-backend/educloud-analytics`
  - `git commit -m "feat(analytics): implement multi-source batch extractor and historical metrics rebuild engine"`

---

### 任务 6：REST 控制器、安全授权、JWKS 配置与 Gateway 路由

**文件：**
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/TeacherAnalyticsController.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/AdminAnalyticsController.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/FinanceAnalyticsController.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/AuditEventController.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/config/SecurityConfig.java`
- 创建：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/security/InternalApiFilter.java`
- 修改：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/controller/TeacherAnalyticsControllerTest.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/controller/AdminAnalyticsControllerTest.java`
- 测试：`educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/controller/AuditEventControllerTest.java`

- [ ] **步骤 1：编写 MockMvc Controller 测试**
  - 测试教师端接口解析 `X-User-Id`；
  - 测试管理端接口未授权 401/403 拦截，管理员授权成功访问；
  - 测试审计日志分页过滤接口。
- [ ] **步骤 2：实现 4 个 Controller 类与 SecurityConfig**
- [ ] **步骤 3：在 Gateway `application.yml` 中新增 `educloud-analytics` 路由**
- [ ] **步骤 4：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-analytics/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 5：Commit**
  - `git add educloud-backend/educloud-analytics educloud-backend/educloud-gateway`
  - `git commit -m "feat(analytics): implement REST controllers, jwks security config and gateway routes"`

---

### 任务 7：前端双端门户真实数据对接（教师端分析大屏 + 管理端运营/财务/审计大屏）

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/services/api.ts`
- 修改：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx`
- 修改：`educloud-frontend/teacher-portal/src/pages/Dashboard.tsx`
- 修改：`educloud-frontend/admin-portal/src/services/api.ts`
- 修改：`educloud-frontend/admin-portal/src/pages/Dashboard.tsx`
- 修改：`educloud-frontend/admin-portal/src/pages/Finance.tsx`
- 修改：`educloud-frontend/admin-portal/src/pages/Logs.tsx`

- [ ] **步骤 1：教师端 API 替换与页面改造**
  - 在 `teacher-portal/src/services/api.ts` 中移除 Mock，全面接入 `/api/v1/analytics/teacher/**`；
  - 在 `Analytics.tsx` 与 `Dashboard.tsx` 中绑定真实数据。
- [ ] **步骤 2：管理端 API 替换与页面改造**
  - 在 `admin-portal/src/services/api.ts` 中将 `dashboardApi`、`financeApi`、`logApi` 全面对接 `/api/v1/analytics/admin/**`；
  - 在 `Dashboard.tsx`、`Finance.tsx`、`Logs.tsx` 中绑定真实数据，并提供一键重算与任务进度提示。
- [ ] **步骤 3：运行前端生产构建验证**
  - 分别在 `teacher-portal` 与 `admin-portal` 运行 `npm run build`；
  - 预期：TypeScript 类型检查通过，0 错误。
- [ ] **步骤 4：Commit**
  - `git add educloud-frontend/teacher-portal educloud-frontend/admin-portal`
  - `git commit -m "feat(frontend): connect teacher and admin portals to real analytics backend apis"`

---

### 任务 8：虚拟机自动化部署、一键拉起脚本与全链路 E2E 验证

**文件：**
- 修改：`deploy/scripts/start-dev.sh`
- 创建：`deploy/tests/test_analytics_e2e.py`
- 创建：`scratch/sync_and_verify_analytics.py`

- [ ] **步骤 1：更新 `deploy/scripts/start-dev.sh`**
  - 新增 `[12/12] educloud-analytics` 启动段（端口 8101/8102、环境变量注入、探针阻塞检查直到 UP）。
- [ ] **步骤 2：编写端到端自动化集成测试脚本 `test_analytics_e2e.py`**
  - 覆盖：健康检查 ➔ 教师端数据隔离 ➔ 管理员触发全量指标重算 ➔ 校验日度指标生成 ➔ 校验大屏概览、用户增长双折线、分类环形图、财务月度趋势 ➔ 校验全平台审计日志检索。
- [ ] **步骤 3：增量同步至 VM 并在虚拟机上编译与拉起**
  - 运行 `python scratch/sync_and_verify_analytics.py`，上传代码、在 VM 执行 Maven 编译与一键启动。
- [ ] **步骤 4：运行 E2E 自动化测试与真实浏览器 MCP Chrome DevTools 验证**
  - 运行 `python deploy/tests/test_analytics_e2e.py` 验证 100% 通过；
  - 使用 MCP Chrome DevTools 检查教师端分析页、管理端看板、财务页与审计日志页，确保 F12 控制台 0 错误。
- [ ] **步骤 5：Commit**
  - `git add deploy/scripts/start-dev.sh deploy/tests/test_analytics_e2e.py scratch/sync_and_verify_analytics.py`
  - `git commit -m "test(analytics): add e2e test suite, vm startup script and deployment automation"`
