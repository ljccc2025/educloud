# EduCloud M11 全局搜索中心（educloud-search）实施计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建 EduCloud M11 全局搜索中心微服务（`educloud-search`），基于 Elasticsearch 8.14.0 实现多字段加权全文检索、智能搜索建议/自动补全、多维聚合统计、RabbitMQ 领域事件实时增量同步、Alias 零停机平滑全量重建及 MySQL 故障优雅降级，并完成前端双端门户联动。

**架构：** 基于 Spring Boot 3.2.5 + Elasticsearch Java Client 8.14.0 + MyBatis-Plus 3.5.12 + RabbitMQ + Redis，统一经 API 网关（8080）对外暴露 REST API（8099 业务 / 8100 监控探针），独立逻辑数据库 `educloud_search`，支持学生端搜索高亮/联想与管理端索引运维控制台。

**技术栈：** Java 17、Spring Boot 3.2.5、Spring Cloud Alibaba Nacos 2023.0.1.0、Elasticsearch 8.14.0、MyBatis-Plus 3.5.12、JJWT 0.12.5、RabbitMQ 3.13、Redis 7.2、MySQL 8.0.36、React 18、TypeScript 5、Playwright。

---

## 任务列表

### 任务 1：数据库迁移脚本、Elasticsearch Mapping 定义与 Maven 工程配置

**文件：**
- 创建：`deploy/sql/search/V000__technical_tables.sql`
- 创建：`deploy/sql/search/V001__search_tasks.sql`
- 创建：`deploy/sql/user/V011__search_permissions.sql`
- 创建：`educloud-backend/educloud-search/src/main/resources/elasticsearch/course-v1.json`
- 修改：`educloud-backend/pom.xml`
- 创建：`educloud-backend/educloud-search/pom.xml`
- 创建：`educloud-backend/educloud-search/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/SearchApplication.java`

- [ ] **步骤 1：编写数据库初始化与权限 SQL 脚本**
  - `deploy/sql/search/V000__technical_tables.sql`：建立服务元数据与系统表；
  - `deploy/sql/search/V001__search_tasks.sql`：创建 `search_index_task` 与 `search_sync_inbox` 表；
  - `deploy/sql/user/V011__search_permissions.sql`：向 `educloud_user.sys_permission` 插入权限码 161 (`search:rebuild`)、162 (`search:admin:view`) 并授权给 `ROLE_ADMIN`。

- [ ] **步骤 2：定义 Elasticsearch 物理索引 Schema（`course-v1.json`）**
  - 包含 `id`, `courseId`, `title`, `subtitle`, `description`, `category`, `priceCents`, `isFree`, `rating`, `studentCount`, `lessons`, `aggregateVersion` 等字段，配置 standard 分词与 completion 建议字段。

- [ ] **步骤 3：配置 Maven `educloud-search/pom.xml` 与父 POM**
  - 父 POM 注册 `<module>educloud-search</module>`；
  - 子 POM 引入 `educloud-common`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-oauth2-resource-server`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、`spring-boot-starter-data-redis`、`spring-boot-starter-amqp`、`co.elastic.clients:elasticsearch-java:8.14.0`、`elasticsearch-rest-client`、`spring-cloud-starter-alibaba-nacos-discovery` 及测试依赖。

- [ ] **步骤 4：配置 application.yml 与启动类 SearchApplication**
  - 设定业务端口 `8099`、监控端口 `8100`、Nacos 服务名 `educloud-search`、MySQL 连接 `educloud_search`、ES 主机 `127.0.0.1:9200`、RabbitMQ 与 Redis。

- [ ] **步骤 5：运行构建验证**
  - 运行 `mvn -f educloud-backend/educloud-search/pom.xml compile`
  - 预期：BUILD SUCCESS。

- [ ] **步骤 6：Commit**
  - `git add deploy/sql/search deploy/sql/user/V011__search_permissions.sql educloud-backend/pom.xml educloud-backend/educloud-search`
  - `git commit -m "feat(search): add sql migrations, es schema and maven project scaffold"`

---

### 任务 2：数据持久层实体、Mapper 与 Elasticsearch 客户端配置

**文件：**
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/enums/TaskType.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/enums/TaskStatus.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/entity/IndexTaskEntity.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/entity/SearchInboxEntity.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/mapper/IndexTaskMapper.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/mapper/SearchInboxMapper.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/document/CourseIndexDoc.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/document/LessonDoc.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/config/ElasticsearchConfig.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/IndexInitializerService.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/mapper/IndexTaskMapperTest.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/document/CourseIndexDocTest.java`

- [ ] **步骤 1：编写 IndexTaskMapper 单元测试与 Document 序列化测试**
- [ ] **步骤 2：实现实体类、枚举与 Mapper**
- [ ] **步骤 3：实现 ElasticsearchConfig 与 IndexInitializerService（自动创建物理索引与别名）**
- [ ] **步骤 4：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-search/pom.xml test`
  - 预期：Tests run: 2, Failures: 0, Errors: 0, Skipped: 0。
- [ ] **步骤 5：Commit**
  - `git add educloud-backend/educloud-search`
  - `git commit -m "feat(search): implement persistence layer, es document model and index initializer"`

---

### 任务 3：核心搜索服务与智能前缀补全（SearchService & SuggestService）

**文件：**
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/dto/request/CourseSearchQuery.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/dto/response/CourseSearchResponse.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/dto/response/CourseSearchItem.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/dto/response/SearchAggregations.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/dto/response/SuggestResponse.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/dto/response/SuggestItem.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/SearchService.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/SuggestService.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/impl/SearchServiceImpl.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/impl/SuggestServiceImpl.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/fallback/DatabaseFallbackSearchService.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/service/SearchServiceTest.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/service/SuggestServiceTest.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/service/DatabaseFallbackSearchServiceTest.java`

- [ ] **步骤 1：编写 SearchService 单元测试**（测试多字段加权搜索、分类难度价格多重过滤、多维度聚合提取、高亮标签提取、降级逻辑切换）
- [ ] **步骤 2：编写 SuggestService 单元测试**（测试关键词前缀补全与课程命中）
- [ ] **步骤 3：实现 DTO 模型与响应对象**
- [ ] **步骤 4：实现 SearchServiceImpl、SuggestServiceImpl 与 DatabaseFallbackSearchService**
- [ ] **步骤 5：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-search/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 6：Commit**
  - `git add educloud-backend/educloud-search`
  - `git commit -m "feat(search): implement multi-field weighted search, facets aggregation, suggest and db fallback"`

---

### 任务 4：RabbitMQ 领域事件驱动与实时索引增量同步

**文件：**
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/messaging/event/CourseDomainEvent.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/messaging/event/ContentDomainEvent.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/IndexSyncService.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/impl/IndexSyncServiceImpl.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/messaging/CourseIndexConsumer.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/messaging/ContentIndexConsumer.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/messaging/CourseIndexConsumerTest.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/messaging/ContentIndexConsumerTest.java`

- [ ] **步骤 1：编写 Consumer 单元测试**（测试 `CoursePublished` 自动 Upsert、`CourseOffline` 自动删除、`LessonPublished` 局部更新课件列表、版本落后消息幂等 ACK 忽略）
- [ ] **步骤 2：实现 IndexSyncService 与幂等收件箱校验逻辑**
- [ ] **步骤 3：实现 CourseIndexConsumer 与 ContentIndexConsumer 监听器**
- [ ] **步骤 4：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-search/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 5：Commit**
  - `git add educloud-backend/educloud-search`
  - `git commit -m "feat(search): implement rabbitmq domain event consumers and idempotent sync engine"`

---

### 任务 5：管理端全量索引平滑重建服务（IndexRebuildService）

**文件：**
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/IndexRebuildService.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/service/impl/IndexRebuildServiceImpl.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/support/CourseDataExtractor.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/service/IndexRebuildServiceTest.java`

- [ ] **步骤 1：编写 IndexRebuildService 单元测试**（测试任务号生成、批量拉取课程数据、新索引创建与 Bulk 写入、别名原子切换、失败状态回退与记录）
- [ ] **步骤 2：实现 CourseDataExtractor 与 IndexRebuildServiceImpl**
- [ ] **步骤 3：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-search/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 4：Commit**
  - `git add educloud-backend/educloud-search`
  - `git commit -m "feat(search): implement zero-downtime full index rebuild service with atomic alias swap"`

---

### 任务 6：REST 控制器、安全授权、JWKS 配置与 Gateway 路由

**文件：**
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/controller/SearchController.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/controller/SearchAdminController.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/config/SecurityConfig.java`
- 创建：`educloud-backend/educloud-search/src/main/java/com/educloud/search/security/InternalApiFilter.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/controller/SearchControllerTest.java`
- 测试：`educloud-backend/educloud-search/src/test/java/com/educloud/search/controller/SearchAdminControllerTest.java`

- [ ] **步骤 1：编写 Controller 单元测试**（MockMvc 测试公开搜索接口 `/api/v1/search/courses`、前缀联想 `/api/v1/search/suggest`、管理员鉴权接口 `/api/v1/search/admin/rebuild-index` 权限码 `search:rebuild` 拦截）
- [ ] **步骤 2：实现 SearchController 与 SearchAdminController**
- [ ] **步骤 3：实现 SecurityConfig 与 InternalApiFilter**
- [ ] **步骤 4：运行测试验证通过**
  - 运行 `mvn -f educloud-backend/educloud-search/pom.xml test`
  - 预期：All tests pass。
- [ ] **步骤 5：Commit**
  - `git add educloud-backend/educloud-search`
  - `git commit -m "feat(search): implement REST controllers, jwks security config and access controls"`

---

### 任务 7：前端双端门户联动（学生端搜索框联想/高亮与管理端索引看板）

**文件：**
- 修改：`educloud-frontend/student-portal/src/services/api.ts`
- 创建：`educloud-frontend/student-portal/src/components/search/SuggestDropdown.tsx`
- 修改：`educloud-frontend/student-portal/src/components/Navbar.tsx`
- 修改：`educloud-frontend/student-portal/src/pages/Courses.tsx`
- 创建：`educloud-frontend/admin-portal/src/services/searchAdminApi.ts`
- 创建：`educloud-frontend/admin-portal/src/pages/SearchAdmin.tsx`
- 修改：`educloud-frontend/admin-portal/src/App.tsx`
- 修改：`educloud-frontend/admin-portal/src/components/Sidebar.tsx`

- [ ] **步骤 1：学生端 API 扩展与 SuggestDropdown 联想浮层**
  - 在 `api.ts` 中封装 `searchCourses(query)` 与 `fetchSearchSuggestions(q)`；
  - 在 `Navbar.tsx` 中集成 300ms 防抖实时联想浮层，支持键盘上下切换与回车跳转。
- [ ] **步骤 2：学生端 Courses.tsx 结果页高亮与 Facets 多维筛选**
  - 接收 URL 参数 `keyword`，调用全局搜索 API 渲染带有 `<em class="search-highlight">` 醒目样式的课程卡片，联动左侧分类/难度聚合统计。
- [ ] **步骤 3：管理端 SearchAdmin.tsx 索引管理看板与侧边栏接入**
  - 提供一键触发全量索引重建按钮、实时刷新进度条与历史任务记录列表。
- [ ] **步骤 4：运行前端构建验证**
  - 在 `student-portal` 和 `admin-portal` 分别运行 `npm run build`
  - 预期：TypeScript 编译通过，0 错误。
- [ ] **步骤 5：Commit**
  - `git add educloud-frontend/student-portal educloud-frontend/admin-portal`
  - `git commit -m "feat(frontend): implement search suggest dropdown, highlight rendering and search admin dashboard"`

---

### 任务 8：虚拟机自动化部署、一键拉起脚本与全链路 E2E 验证

**文件：**
- 修改：`deploy/scripts/start-dev.sh`
- 创建：`deploy/tests/test_search_e2e.py`
- 创建：`scratch/sync_and_verify_search.py`

- [ ] **步骤 1：更新 `deploy/scripts/start-dev.sh` 一键启动脚本**
  - 新增 `[11/11] educloud-search` 启动段（端口 8099/8100、环境变量注入、探针阻塞检查直到 UP）。
- [ ] **步骤 2：编写端到端自动化测试脚本 `test_search_e2e.py`**
  - 验证全流程：初始化 ES 索引 ➔ 触发全量重建 ➔ 校验课程文档批量写入 ➔ 模拟发布新课异步增量同步 ➔ 调用 `/search/courses` 检索高亮 ➔ 调用 `/search/suggest` 验证前缀联想 ➔ 管理端任务状态查询。
- [ ] **步骤 3：增量同步到 VM 并执行一键启动**
  - 运行 `python scratch/sync_and_verify_search.py` 自动上传代码、在 VM 执行 Maven 编译与 `start-dev.sh`。
- [ ] **步骤 4：执行端到端自动化与 Playwright 浏览器视觉验收**
  - 执行 `python deploy/tests/test_search_e2e.py` 验证 100% 通过；
  - 使用 Playwright 检查学生端搜索框联想与管理端索引看板。
- [ ] **步骤 5：Commit**
  - `git add deploy/scripts/start-dev.sh deploy/tests/test_search_e2e.py scratch/sync_and_verify_search.py`
  - `git commit -m "test(search): add e2e test suite, vm startup script and deployment automation"`
