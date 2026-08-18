# EduCloud 后端完整实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task.
>
> **Required workflow:** `test-driven-development` for every behavior change, `requesting-code-review` at each phase checkpoint, and `verification-before-completion` before any completion claim.

**Goal:** 在不改变现有三端信息架构的前提下，把 EduCloud 从前端 Mock 演进为 Gateway 加 11 个业务服务的可验证微服务系统。

**Architecture:** Java 17、Spring Boot 3.2.5、Spring Cloud 2023.0.3、Spring Cloud Alibaba 2023.0.1.0；每服务独立逻辑数据库；同步 REST、RabbitMQ 事件、本地事务加 Outbox、消费者幂等；三套门户统一经 Gateway 访问。

**Tech Stack:** Nacos 2.3.2、MyBatis-Plus 3.5.5、JJWT 0.12.5、Knife4j 4.4.0、MySQL 8.0.36、Redis 7.2、RabbitMQ 3.13、Elasticsearch 8.14、MinIO Java SDK 8.5.7、Zipkin、Prometheus、Grafana、Docker Compose、Kubernetes 1.29、Helm；现有 React 18.3.1、TypeScript 5.5.4、Vite 5.4 前端。

---

## 0. 执行规则

1. 开始每项任务前运行 `git status --short` 和对应文件的 `git diff`。
2. 当前工作区已有用户未提交改动。禁止 `git add -A`、`git stash`、恢复已删除文档或覆盖不属于本任务的修改。
3. 每项行为先写失败测试，确认失败原因正确，再写最小实现，最后重构。
4. 每次提交只暂存任务列出的路径，例如：

   ```powershell
   git add -- educloud-backend/educloud-user deploy/sql/user
   git commit -m "feat(user): implement login"
   ```

5. 没有实际执行验证命令时，不写“通过”。没有外部凭据时，支付、媒体直播和 AI 必须保留模拟/待决策标识。
6. 阶段检查点使用 `requesting-code-review`；发现评审意见后先使用 `receiving-code-review` 验证再修改。
7. 后端根命令统一从仓库根执行：

   ```powershell
   mvn -f educloud-backend/pom.xml verify
   ```

## 1. 目标目录

```text
educloud-backend/
├─ pom.xml
├─ educloud-common/
├─ educloud-gateway/
├─ educloud-user/
├─ educloud-course/
├─ educloud-content/
├─ educloud-order/
├─ educloud-payment/
├─ educloud-live/
├─ educloud-file/
├─ educloud-notification/
├─ educloud-analytics/
├─ educloud-search/
└─ educloud-recommendation/

deploy/
├─ docker-compose/
├─ sql/<service>/
├─ scripts/
├─ tests/
├─ kubernetes/
├─ helm/
└─ runbooks/
```

---

## 阶段 0：工程基础与统一契约

### Task 1：建立 Maven 聚合工程和空应用

**Files:**

- Create: `educloud-backend/pom.xml`
- Create: `educloud-backend/educloud-common/pom.xml`
- Create: `educloud-backend/educloud-gateway/pom.xml`
- Create: `educloud-backend/educloud-<service>/pom.xml`，覆盖 11 个业务服务
- Create: `educloud-backend/educloud-<service>/src/main/java/com/educloud/<service>/<Service>Application.java`
- Create: `educloud-backend/educloud-<service>/src/main/resources/application.yml`

**Steps:**

1. 先运行 `mvn -f educloud-backend/pom.xml help:effective-pom`，确认因文件不存在而失败。
2. 创建父 POM，锁定 Java 17、Boot 3.2.5、Cloud 2023.0.3、Alibaba 2023.0.1.0，以及 JUnit 5.10、Mockito 5.11、Testcontainers 1.19 等文档规定依赖。
3. 创建 Common、Gateway 和 11 个服务模块；每个应用只包含启动类与 Nacos 服务名/端口。
4. 运行：

   ```powershell
   mvn -f educloud-backend/pom.xml help:effective-pom
   mvn -f educloud-backend/pom.xml -DskipTests package
   ```

   预期：所有模块被 Reactor 识别并构建成功。
5. 仅暂存 `educloud-backend` 新文件，提交 `chore(backend): scaffold service modules`。

### Task 2：统一响应、分页和异常契约

**Files:**

- Create: `educloud-backend/educloud-common/src/test/java/com/educloud/common/api/ApiResponseTest.java`
- Create: `educloud-backend/educloud-common/src/test/java/com/educloud/common/web/GlobalExceptionHandlerTest.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/api/ApiResponse.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/api/PageResponse.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/error/ErrorCode.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/error/BusinessException.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/web/GlobalExceptionHandler.java`

**Steps:**

1. 写失败测试，断言成功响应含 `code/data/requestId/timestamp`，校验失败为 HTTP 400，业务冲突为 409，未知异常为 500 且不泄露堆栈。
2. 运行 `mvn -f educloud-backend/pom.xml -pl educloud-common test`，确认编译或断言失败。
3. 实现不可变响应、分页结构、错误码和全局异常映射；业务码不替代 HTTP 状态。
4. 再运行同一测试，并运行 `mvn -f educloud-backend/pom.xml -pl educloud-common -am verify`。
5. 提交 `feat(common): add api and error contracts`。

### Task 3：请求追踪和安全上下文

**Files:**

- Create: `educloud-backend/educloud-common/src/test/java/com/educloud/common/web/RequestContextFilterTest.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/web/RequestContextFilter.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/security/AuthenticatedUser.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/security/SecurityContextFacade.java`
- Create: `educloud-backend/educloud-common/src/test/java/com/educloud/common/id/WorkerLeaseIdentifierGeneratorTest.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/id/WorkerLeaseIdentifierGenerator.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/config/CommonWebConfiguration.java`

**Steps:**

1. 测试缺失请求 ID 时生成、合法 ID 时保留、响应头回传、MDC 请求结束后清理。
2. 测试匿名安全上下文不会伪造用户，认证用户 ID 以字符串安全解析。
3. 运行 Common 测试确认失败。
4. 实现过滤器、只读安全上下文和 Redis Worker 槽位租约；测试 32 个实例无碰撞、第 33 个安全失败、租约丢失停止写入和时钟回拨。
5. 运行 Common `verify`，提交 `feat(common): add request context`。

### Task 4：本地基础设施和独立逻辑数据库

**Files:**

- Create: `deploy/docker-compose/compose.yml`
- Create: `deploy/docker-compose/.env.example`
- Create: `deploy/sql/bootstrap/001-create-databases.sql`
- Create: `deploy/sql/common/schema-migration-history.sql`
- Create: `deploy/scripts/apply-migrations.ps1`
- Create: `deploy/scripts/apply-migrations.sh`
- Create: `deploy/tests/migration-runner-tests.ps1`
- Create: `deploy/scripts/verify-compose.ps1`
- Modify: `.gitignore`

**Steps:**

1. 先运行 `docker compose -f deploy/docker-compose/compose.yml config`，确认文件不存在而失败。
2. 增加固定版本的 MySQL、Redis、RabbitMQ、Elasticsearch、MinIO、Nacos、Zipkin、Prometheus 和 Grafana；加入健康检查与项目专用卷。
3. 初始化 11 个逻辑数据库和最小权限账号，不授予全局 root 权限；实现带 SHA-256、`schema_migration_history`、MySQL 命名锁和 FAILED 阻断的迁移执行器。
4. 测试空库顺序执行、checksum 篡改、两个执行器并发、脚本部分失败后拒绝继续；运行 `docker compose -f deploy/docker-compose/compose.yml config`，环境允许时启动依赖并运行 `./deploy/scripts/verify-compose.ps1`。
5. 若 Docker 不可用，明确记录运行验证未完成，不能声称服务健康。
6. 提交 `chore(deploy): add local infrastructure`。

### Task 5：Outbox、Inbox 和幂等技术模板

**Files:**

- Create: `educloud-backend/educloud-common/src/test/java/com/educloud/common/messaging/EventEnvelopeTest.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/messaging/EventEnvelope.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/messaging/OutboxRecord.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/messaging/InboxRecord.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/idempotency/IdempotencyKey.java`
- Create: `deploy/sql/common/outbox-inbox-template.sql`
- Create: `deploy/sql/common/audit-event-template.sql`
- Create: `deploy/sql/user/V000__technical_tables.sql`
- Create: `deploy/sql/course/V000__technical_tables.sql`
- Create: `deploy/sql/content/V000__technical_tables.sql`
- Create: `deploy/sql/order/V000__technical_tables.sql`
- Create: `deploy/sql/payment/V000__technical_tables.sql`
- Create: `deploy/sql/live/V000__technical_tables.sql`
- Create: `deploy/sql/file/V000__technical_tables.sql`
- Create: `deploy/sql/notification/V000__technical_tables.sql`
- Create: `deploy/sql/analytics/V000__technical_tables.sql`
- Create: `deploy/sql/search/V000__technical_tables.sql`
- Create: `deploy/sql/recommendation/V000__technical_tables.sql`
- Create: `deploy/tests/technical-table-contract-tests.ps1`
- Create: `educloud-backend/educloud-common/src/test/java/com/educloud/common/audit/AuditEventServiceTest.java`
- Create: `educloud-backend/educloud-common/src/main/java/com/educloud/common/audit/AuditEventService.java`

**Steps:**

1. 测试事件信封必含事件 ID、类型、契约版本、来源提交顺序 `sourceSequence`、聚合 ID、单调 `aggregateVersion`、时间、requestId 和 traceId；并发事务测试后一个水位不能先提交，回滚不产生可见水位空洞。
2. 测试相同幂等键和不同请求摘要会被识别为冲突；测试业务事务失败时审计和 Outbox 均不落库，成功时 `audit_event` 只追加并发布事件。
3. 运行 Common 测试确认失败。
4. 实现纯技术模板、每来源库单行加锁的提交顺序分配器、派生服务 `consumer_watermark` 和按聚合稳定路由键；测试普通投影绑定完整聚合类型流并 no-op 无关事件，来源级重建队列绑定该服务全部事件并 no-op 无关聚合，首条版本大于 1 的快照引导、重复、倒序、版本缺口和水位提交顺序，不把任何领域 Entity 放入 Common。
5. 把受 checksum 管理的 V000 技术迁移显式安装到 11 个服务目录，包含适用的 `outbox_sequence/outbox_event/inbox_event/consumer_watermark/idempotency_record/audit_event`；模板只作为生成来源，不由 runner 隐式执行。测试 11 个空库实际迁移后表、索引和应用账号权限齐全，特别是 `audit_event` 仅 INSERT/SELECT。
6. 为水位/归档端点建立契约测试：读取最后已提交水位、`afterExclusive/toInclusive` 有界分页、游标参数绑定、越界拒绝、崩溃续传，以及 W1 前 durable 队列到原子切换无缝接管。
7. 运行 Common `verify`、11 库空库迁移和 `technical-table-contract-tests.ps1`，提交 `feat(common): add reliable messaging contracts`。

### Task 6：Gateway 路由、认证和错误处理

**Files:**

- Create: `educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/GatewaySecurityTest.java`
- Create: `educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/RouteContractTest.java`
- Create: `educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/JwtAuthenticationFilter.java`
- Create: `educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/GatewaySecurityConfiguration.java`
- Create: `educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/error/GatewayErrorHandler.java`
- Modify: `educloud-backend/educloud-gateway/src/main/resources/application.yml`

**Steps:**

1. 写失败测试：公开课程/登录可访问，受保护接口无 Token 返回 401，伪造 `X-User-Id` 被移除，过期/错误受众 Token 被拒绝。
2. 写路由契约测试，覆盖 11 个服务及重叠路径：`/courses/{id}` 与 `/courses/{id}/chapters`、`/courses/{id}/content-drafts`、`/teacher/courses/{id}/content-draft`、`/me` 与 `/me/exams`，并覆盖 `/ws/v1/live/**`。
3. 实现 JWT 公钥验证、Redis `sid/tokenVersion` 在线撤销、请求 ID、精确 CORS 和统一 Gateway 错误。
4. 运行 `mvn -f educloud-backend/pom.xml -pl educloud-gateway -am verify`。
5. 提交 `feat(gateway): enforce routes and authentication`。

### Task 7：基础 CI

**Files:**

- Create: `.github/workflows/backend-ci.yml`
- Create: `.github/workflows/frontend-ci.yml`
- Create: `educloud-backend/README.md`
- Create: `educloud-frontend/e2e/package.json`
- Create: `educloud-frontend/e2e/package-lock.json`
- Create: `educloud-frontend/e2e/playwright.config.ts`
- Create: `educloud-frontend/e2e/tests/auth-smoke.spec.ts`

**Steps:**

1. CI 后端运行 Java 17 和 `mvn -f educloud-backend/pom.xml verify`。
2. 学生端使用 pnpm frozen lock；教师/管理端分别 `npm ci`、`typecheck`、`build`；独立 E2E 工程固定 Playwright 1.47 并执行 smoke test。
3. 不在未验证前强行固定 Node 版本；先记录当前可用版本并创建后续锁定任务。
4. 使用本地等价命令验证 YAML 和构建；不能执行远端 CI 时标注未验证。
5. 提交 `ci: add backend and frontend verification`。

### 阶段 0 检查点

- 运行后端全量 `mvn -f educloud-backend/pom.xml verify`。
- 运行 Compose 配置验证。
- 使用 `requesting-code-review` 检查版本、Common 边界、Gateway 安全和用户改动保护。

---

## 阶段 1：身份、文件、课程和内容主链路

### Task 8：User 数据库和 Mapper

**Files:**

- Create: `deploy/sql/user/V001__user_identity_and_rbac.sql`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/mapper/UserSchemaIntegrationTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/SysUserEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/SysUserMapper.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/UserProfileEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/SysRoleEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/SysPermissionEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/RefreshSessionEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/UserProfileMapper.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/SysRoleMapper.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/SysPermissionMapper.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/RefreshSessionMapper.java`

**Steps:**

1. 先写 MySQL 集成测试，验证用户名/邮箱唯一、会话 Token 哈希唯一、角色关系唯一和乐观锁字段。
2. 运行 `mvn -f educloud-backend/pom.xml -pl educloud-user -am test`，确认缺表失败。
3. 按数据设计实现 V001 和最小 Entity/Mapper。
4. 在 MySQL 8.0.36 执行空库迁移和重复唯一约束测试。
5. 提交 `feat(user): add identity schema`。

### Task 9：真实登录和账号保护

**Files:**

- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/service/AuthenticationServiceTest.java`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/controller/AuthControllerTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/service/AuthenticationService.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/AuthController.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/dto/request/RegisterStudentRequest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/dto/request/LoginRequest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/dto/response/LoginResponse.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/config/PasswordConfiguration.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/LoginAuditEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/LoginAuditMapper.java`

**Steps:**

1. 测试学生注册开关、IP/标识限流、用户名/邮箱唯一并发、密码策略、默认最小权限，以及正确密码、错误密码、锁定、禁用、统一失败语义和不记录密码；教师/管理员不能自助注册。
2. 测试登录响应返回 Access Token，Refresh Token 只在 HttpOnly Cookie。
3. 运行测试确认失败。
4. 实现密码哈希验证、失败计数、锁定和登录审计。
5. 运行 User `verify`，提交 `feat(user): implement secure login`。

### Task 10：Refresh 轮换、注销和会话撤销

**Files:**

- Create: `deploy/sql/user/V002__service_clients.sql`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/service/RefreshSessionServiceTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/service/RefreshSessionService.java`
- Modify: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/AuthController.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/security/JwtKeyProvider.java`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/service/ServiceTokenServiceTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/service/ServiceTokenService.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/InternalServiceTokenController.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/ServiceClientEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/entity/ServiceClientCredentialEntity.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/ServiceClientMapper.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/mapper/ServiceClientCredentialMapper.java`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/command/ServiceClientCredentialCommandTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/command/ServiceClientCredentialCommand.java`
- Create: `deploy/scripts/bootstrap-service-clients.ps1`
- Create: `deploy/scripts/bootstrap-service-clients.sh`
- Create: `deploy/scripts/rotate-service-client.ps1`
- Create: `deploy/scripts/rotate-service-client.sh`

**Steps:**

1. 测试原子刷新、父子 Token、并发宽限、窗口外重用撤销会话族、过期/禁用用户拒绝、注销幂等，以及 Gateway 在注销/禁用后立即拒绝原 Access Token。
2. 测试 JWT `kid/iss/aud/exp/sid/tokenVersion`。
3. 实现 Token 哈希、数据库会话、Redis 活跃状态和非对称签名轮换。
4. 实现并测试 HTTPS + HTTP Basic `client_credentials` 协议、独立凭据表哈希、`ACTIVE/GRACE/REVOKED` 双凭据轮换、指定 `aud/scope` 的 5 分钟服务 Token、接口 ACL 和 `tokenVersion` 撤销；验证并发轮换、宽限到期、凭据不进入 URL/日志且非本地环境拒绝明文交换。
5. 实现非 Web `bootstrap/rotate/revoke/verify` CLI 和脚本：Secret 仅从 stdin/文件读取，不进入参数/stdout；测试相同值幂等、不同值拒绝隐式覆盖、审计和恢复轮换。
6. 运行 User 和 Gateway 安全测试，提交 `feat(user): add refresh session rotation`。

### Task 11：用户档案、RBAC 和公开平台配置

**Files:**

- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/service/AuthorizationServiceTest.java`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/controller/UserAdminControllerTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/MeController.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/UserAdminController.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/RoleController.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/PlatformConfigController.java`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/controller/SigningKeyStatusControllerTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/SigningKeyStatusController.java`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/controller/UserStatusSnapshotControllerTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/UserStatusSnapshotController.java`
- Create: `educloud-backend/educloud-user/src/test/java/com/educloud/user/support/ProfileFileGrantClientContractTest.java`
- Create: `educloud-backend/educloud-user/src/main/java/com/educloud/user/support/ProfileFileGrantClient.java`

**Steps:**

1. 测试本人档案、用户分页脱敏、禁用撤销会话、角色分配权限和平台配置不含 Secret；头像只对已授权用户 DTO 返回短期地址，100 条用户分页只调用一次 File 批量 grant，地址不落库且过期后由新业务请求刷新。
2. 测试普通管理员不能读取密码哈希、SMTP、MinIO、JWT 私钥；签名密钥状态只返回活动 `kid`、公钥数量、更新时间和轮换时间；User 状态快照只允许登记的投影消费者并返回聚合版本。
3. 测试不存在删除/匿名化用户端点；首期只实现禁用/恢复。匿名化须等待专项合规方案、数据依赖清单、双人复核和恢复演练批准。
4. 实现权限码、资源方法授权和审计事件。
5. 运行 User `verify` 和 OpenAPI 契约检查。
6. 提交 `feat(user): add profile rbac and public config`。

### Task 12：File 上传会话和业务绑定

**Files:**

- Create: `deploy/sql/file/V001__file_objects.sql`
- Create: `educloud-backend/educloud-file/src/test/java/com/educloud/file/service/FileUploadServiceTest.java`
- Create: `educloud-backend/educloud-file/src/test/java/com/educloud/file/controller/FileControllerSecurityTest.java`
- Create: `educloud-backend/educloud-file/src/test/java/com/educloud/file/controller/StorageStatusControllerTest.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/service/FileUploadService.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/controller/FileController.java`
- Create: `educloud-backend/educloud-file/src/test/java/com/educloud/file/controller/InternalDownloadGrantControllerTest.java`
- Create: `educloud-backend/educloud-file/src/test/java/com/educloud/file/controller/InternalDownloadGrantBatchControllerTest.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/controller/InternalDownloadGrantController.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/service/DownloadGrantService.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/dto/request/DownloadGrantRequest.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/dto/request/BatchDownloadGrantRequest.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/dto/response/DownloadGrantResponse.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/controller/StorageStatusController.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/support/MinioObjectStorage.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/entity/FileUploadSessionEntity.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/entity/FileObjectEntity.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/entity/FileBindingEntity.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/mapper/FileUploadSessionMapper.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/mapper/FileObjectMapper.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/mapper/FileBindingMapper.java`
- Create: `educloud-backend/educloud-file/src/main/java/com/educloud/file/support/UnboundFileCleanupJob.java`

**Steps:**

1. 测试服务端对象键、类型/大小限制、完成前不可用、重复完成和未绑定清理；不存在只凭 `fileId` 的公共下载签名，内部 grant 从服务 Token 推导 ownerService，拒绝错误 `clientId/ownerType/ownerId/fileId`、伪造 ownerService 字段、未登记 purpose、未绑定/已删除文件和超长 TTL，并审计终端主体；绑定/解绑/删除锁定 `FileObject` 根并递增版本，倒序 `FileBound` 不能在 `FileDeleted` 后复活投影。
2. 运行 File 测试确认失败。
3. 实现上传会话、MinIO 确认、业务绑定、单文件 grant 和最多 100 项的批量 grant；批量逐项校验精确绑定，任一 owner/purpose 越权使整批失败，正确绑定但对象不可用只返回 `UNAVAILABLE`。使用可注入时钟和 MinIO 集成测试证明地址超过 `expiresAt` 后无法读取，响应不产生长期或可重复使用的 capability。
4. 实现 `GET /files/storage-status` 与限频、审计的 `POST /files/storage-tests`；测试响应和请求均无密钥、无权限拒绝、探测对象清理和错误脱敏。
5. 使用 MinIO 测试实例验证上传、确认、下载、解绑、清理和连接探测。
6. 提交 `feat(file): implement secure file lifecycle`。

### Task 13：Course 数据库和公开课程查询

**Files:**

- Create: `deploy/sql/course/V001__course_catalog.sql`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/mapper/CourseSchemaIntegrationTest.java`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/service/CourseQueryServiceTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/controller/CourseQueryController.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/entity/CourseEntity.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/entity/CourseCategoryEntity.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/mapper/CourseMapper.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/mapper/CourseCategoryMapper.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/service/CourseQueryService.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/dto/response/CourseSummaryResponse.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/dto/response/CourseDetailResponse.java`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/support/CourseCoverFileGrantClientContractTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/support/CourseCoverFileGrantClient.java`

**Steps:**

1. 测试仅返回已发布课程、分页/排序白名单、分类过滤和字符串 ID/十进制金额；课程封面使用短期 `coverUrl`，100 条课程分页只调用一次 File 批量 grant。
2. 测试课程详情不泄露内部审核快照；匿名 `PUBLIC_CATALOG` 只能为当前已发布结果签封面，不能以伪造 courseId/ownerId 取得草稿、下架或跨课程文件。
3. 实现 Schema、Mapper 和查询服务；缓存只作为可失效优化。
4. 运行 Course `verify` 和 MySQL 集成测试。
5. 提交 `feat(course): add catalog queries`。

### Task 14：教师课程维护和审核状态机

**Files:**

- Create: `deploy/sql/course/V002__content_readiness_projection.sql`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/service/CourseLifecycleServiceTest.java`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/controller/CourseAuditControllerTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/service/CourseLifecycleService.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/controller/TeacherCourseController.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/controller/CourseAuditController.java`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/messaging/ContentReadinessProjectionConsumerTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/ContentReadinessProjectionConsumer.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/entity/CourseContentReadinessProjectionEntity.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/mapper/CourseContentReadinessProjectionMapper.java`

**Steps:**

1. 测试发布版与草稿版并存、公开读取不受草稿影响、提交后版本不可变、撤回、禁止自审、拒绝原因、版本冲突和发布事件；测试下架、仅有效 `OFFLINE` 重新上架、归档终态和各权限；首次提交/审批在 Content 就绪投影缺失、不就绪或版本有缺口时失败关闭。
2. 测试 Course 对 `CourseContent` 完整事件流按版本消费，`ContentRevisionPublished` 更新投影、无关事件 no-op 推进水位、首条高版本先快照引导。
3. 运行测试确认失败。
4. 实现 `course/course_version/course_audit_submission` 状态机与内容就绪投影，审批原子切换发布版本，业务更新和 Outbox 同事务。
5. 测试重复审核不会重复发布事件。
6. 提交 `feat(course): implement review lifecycle`。

### Task 15：选课、课程学生和评价

**Files:**

- Create: `deploy/sql/course/V003__enrollment_and_review.sql`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/service/EnrollmentServiceTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/service/EnrollmentService.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/controller/EnrollmentController.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/controller/CourseReviewController.java`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/controller/EnrollmentAccessSnapshotControllerTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/controller/EnrollmentAccessSnapshotController.java`

**Steps:**

1. 测试免费课程幂等选课、付费课程拒绝直接选课、唯一关系、教师学生列表归属和评价范围；Enrollment 快照只允许 Content 并携带连续聚合版本。
2. 实现 Enrollment/Review 表与服务。
3. 发布 `EnrollmentCreated/Revoked`，重复命令不重复计数。
4. 运行 Course `verify`。
5. 提交 `feat(course): add enrollment and reviews`。

### Task 16：Content 目录和课件

**Files:**

- Create: `deploy/sql/content/V001__curriculum.sql`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/CurriculumServiceTest.java`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/controller/CurriculumSecurityTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/service/CurriculumService.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/CurriculumController.java`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ContentAuditServiceTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/ContentAuditSubmissionEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/ContentAuditSubmissionMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ContentAuditService.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ContentAuditController.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/ContentAuditEventPublisher.java`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/controller/ContentReadinessSnapshotControllerTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ContentReadinessSnapshotController.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/CourseChapterEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/CoursewareEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/CourseChapterMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/CoursewareMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/request/SaveChapterRequest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/request/SaveCoursewareRequest.java`

**Steps:**

1. 测试发布修订与草稿修订并存、提交后不可变、教师归属、章节排序唯一、文件可用性、免费预览和未选课课件地址隐藏。
2. 测试内容修订提交、撤回、禁止自审、驳回原因、驳回后复制新修订、重复审批和版本冲突；就绪快照只允许 Course 用于 bootstrap/gap repair 并返回内容根聚合版本。
3. 在 `V001__curriculum.sql` 实现 `course_content/content_revision/content_audit_submission`、章节和课件；实现审核服务/Controller、File 绑定事件，审批在同一本地事务原子切换发布修订并写审计/Outbox `ContentRevisionPublished`。
4. 删除被历史引用课件时改为不可见而非破坏历史。
5. 运行 Content `verify`。
6. 提交 `feat(content): add curriculum management`。

### 阶段 1 检查点

- 执行 User/File/Course/Content 集成验证（包含 `ContentRevisionPublished`→Course 就绪投影→首次课程发布）和全量 `mvn -f educloud-backend/pom.xml verify`。
- 由 `requesting-code-review` 检查认证、敏感配置、课程审核、文件越权和数据所有权。
- 本阶段尚未接前端时，文档状态仍不得改为已实现的完整产品闭环。

---

## 阶段 2：学习、作业、考试和首轮前端联调

### Task 17：学习访问与进度

**Files:**

- Create: `deploy/sql/content/V002__learning_progress.sql`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/LearningProgressServiceTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/service/LearningProgressService.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/LearningProgressController.java`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/controller/CoursewareDownloadControllerTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/CoursewareDownloadController.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/support/FileDownloadGrantClient.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/EnrollmentChangedConsumer.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/CourseAccessProjectionEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/CourseAccessProjectionMapper.java`

**Steps:**

1. 测试未选课拒绝、事件创建访问投影、位置不倒退、异常增量限制和重复完成幂等；课件下载先验证有效选课/教师归属/免费预览，再以服务 Token 和精确课件绑定向 File 申请 grant。覆盖伪造 `fileId/ownerId`、跨课程课件、退款或撤销选课、普通用户绕过 Content、File grant 地址过期后复用，均失败关闭。
2. 实现进度表、服务端总进度计算和 `LearningProgressChanged`。
3. 测试退款/撤销选课后禁止继续访问但保留历史。
4. 运行 Content 和 RabbitMQ 集成测试。
5. 提交 `feat(content): persist learning progress`。

### Task 18：作业发布、提交和批改

**Files:**

- Create: `deploy/sql/content/V003__assignments.sql`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/AssignmentServiceTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/service/AssignmentService.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/AssignmentController.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/AssignmentEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/AssignmentSubmissionEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/AssignmentMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/AssignmentSubmissionMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/request/SubmitAssignmentRequest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/request/GradeAssignmentRequest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/AssignmentEventPublisher.java`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/controller/AssignmentAttachmentDownloadTest.java`

**Steps:**

1. 测试发布校验、截止时间、迟交、尝试次数、附件权限、重复提交和派生学生状态；附件响应只在学生本人/课程教师授权后按当前响应一次批量 grant，跨课程、退款/撤销选课和过期地址不可访问。
2. 测试评分 `0..totalScore`、教师归属、版本冲突和重复评分。
3. 实现作业状态机和提交版本。
4. 运行 Content `verify`。
5. 提交 `feat(content): implement assignment workflow`。

### Task 19：考试、答卷和评分

**Files:**

- Create: `deploy/sql/content/V004__exams.sql`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ExamServiceTest.java`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ExamDeadlineTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamService.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ExamController.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/ExamEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/ExamQuestionEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/ExamAttemptEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/ExamAnswerEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/ExamMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/ExamAttemptMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/ExamAnswerMapper.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/request/SaveExamAnswerRequest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/support/ExamDeadlineJob.java`

**Steps:**

1. 使用可注入时钟测试发布时间、分值合计、答卷快照、尝试次数和答案隐藏。
2. 测试保存答案版本、重复交卷、到期自动交卷和客观/主观评分。
3. 实现状态机，调度任务幂等。
4. 运行 Content `verify`。
5. 提交 `feat(content): implement exam lifecycle`。

### Task 20：统一三端认证适配

**Files:**

- Modify: `educloud-frontend/student-portal/src/services/api.ts`
- Modify: `educloud-frontend/student-portal/src/stores/useAuthStore.ts`
- Modify: `educloud-frontend/student-portal/src/App.tsx`
- Modify: `educloud-frontend/teacher-portal/src/services/api.ts`
- Modify: `educloud-frontend/teacher-portal/src/stores/useAuthStore.ts`
- Modify: `educloud-frontend/teacher-portal/src/layouts/TeacherLayout.tsx`
- Modify: `educloud-frontend/teacher-portal/src/App.tsx`
- Modify: `educloud-frontend/admin-portal/src/services/api.ts`
- Modify: `educloud-frontend/admin-portal/src/stores/useAuthStore.ts`
- Modify: `educloud-frontend/admin-portal/src/App.tsx`

**Precondition:** 这些文件可能包含用户未提交改动。逐文件读取 diff 并合并；存在无法安全判断的重叠时停止并请求用户决策。

**Steps:**

1. 先添加可执行的认证契约测试；若仓库仍无前端测试框架，至少以 TypeScript 类型夹具和后端 Controller 契约测试建立失败证据，不擅自引入测试框架。
2. 建立每门户唯一 API Client、内存 Access Token、单次刷新锁和 401 恢复。
3. 教师端增加真实 ProtectedRoute，退出调用后端并清理状态。
4. 保留 UI 布局，移除 localStorage Token 事实来源。
5. 分别运行三端 `typecheck/build`，提交 `feat(frontend): integrate authentication`。

### Task 21：学生/教师/管理端课程与学习联调

**Files:**

- Modify: `educloud-frontend/student-portal/src/services/api.ts`
- Modify: `educloud-frontend/student-portal/src/stores/useCourseStore.ts`
- Modify: `educloud-frontend/student-portal/src/pages/CourseList.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/CourseDetail.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/MyCourses.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/Learning.tsx`
- Modify: `educloud-frontend/teacher-portal/src/services/api.ts`
- Modify: `educloud-frontend/teacher-portal/src/stores/useCourseStore.ts`
- Modify: `educloud-frontend/teacher-portal/src/pages/CourseManage.tsx`
- Modify: `educloud-frontend/teacher-portal/src/pages/CourseEdit.tsx`
- Modify: `educloud-frontend/teacher-portal/src/pages/ContentManage.tsx`
- Modify: `educloud-frontend/teacher-portal/src/types/index.ts`
- Modify: `educloud-frontend/admin-portal/src/pages/ContentAudit.tsx`
- Modify: `educloud-frontend/admin-portal/src/services/api.ts`
- Modify: `educloud-frontend/admin-portal/src/types/index.ts`

**Steps:**

1. 先固定现有 UI 字段与目标 API DTO 映射，加入后端 OpenAPI 契约测试。
2. 替换课程、选课、目录、文件访问和进度 Mock；保留加载/空/错误 UI。
3. 替换教师课程、课程版本审核提交、章节和课件上传 Mock；教师通过 `POST /content-revisions/{revisionId}/submit-review` 提交内容修订，管理端通过精确 Content 审核接口查看、批准或驳回。
4. 验证未选课不能获得受保护文件地址，版本冲突可提示刷新。
5. 运行相关后端测试及三端 `typecheck/build`，提交 `feat(frontend): connect course learning flow`。

### Task 22：作业和考试联调

**Files:**

- Modify: `educloud-frontend/student-portal/src/pages/Assignments.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/Exams.tsx`
- Create: `educloud-frontend/student-portal/src/components/AssignmentSubmissionModal.tsx`
- Create: `educloud-frontend/student-portal/src/components/ExamAttemptModal.tsx`
- Modify: `educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx`
- Modify: `educloud-frontend/teacher-portal/src/pages/ExamManage.tsx`
- Modify: `educloud-frontend/student-portal/src/services/api.ts`
- Modify: `educloud-frontend/student-portal/src/types/index.ts`
- Modify: `educloud-frontend/teacher-portal/src/services/api.ts`
- Modify: `educloud-frontend/teacher-portal/src/types/index.ts`

**Steps:**

1. 用契约测试固定学生状态、提交 DTO、答卷快照和评分错误码。
2. 接通作业提交、查看、批改和冲突处理。
3. 接通考试开始、保存、服务端截止时间、交卷和成绩显示。
4. 防止按钮重复提交；服务端幂等仍必须通过测试。
5. 运行 Content `verify` 和两端 `typecheck/build`，提交 `feat(frontend): connect assessments`。

### 阶段 2 检查点

- 运行 X-04、X-05、X-06 关键场景。
- 评审教师路由保护、课件越权、作业并发评分和考试答案泄露。
- 更新追踪矩阵中已具备真实证据的行；未接通项保持 Mock。

---

## 阶段 3：订单、支付、选课和退款

### Task 23：购物车和订单创建

**Files:**

- Create: `deploy/sql/order/V001__cart_and_orders.sql`
- Create: `educloud-backend/educloud-order/src/test/java/com/educloud/order/service/OrderServiceTest.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/service/OrderService.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/CartController.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/OrderController.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/support/CourseSalesClient.java`
- Create: `educloud-backend/educloud-order/src/test/java/com/educloud/order/controller/OrderFulfillmentSnapshotControllerTest.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/OrderFulfillmentSnapshotController.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/CartItemEntity.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/TradeOrderEntity.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/TradeOrderItemEntity.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/CartItemMapper.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/TradeOrderMapper.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/request/CreateOrderRequest.java`

**Steps:**

1. 测试购物车唯一、服务端价格、下架课程拒绝、免费课程转向直接选课、幂等键和价格快照；测试用户取消只允许 `PENDING_PAYMENT`、版本条件更新并发布唯一 `OrderCancelled`；履约快照只允许 Course 并返回订单项退款范围和 Order 聚合版本。
2. 测试客户端伪造金额不影响应付金额。
3. 实现订单状态机、超时关闭和 `OrderCreated`。
4. 运行 Order `verify` 和 Course 契约测试。
5. 提交 `feat(order): implement cart and order creation`。

### Task 24：支付适配器和回调

**Files:**

- Create: `deploy/sql/payment/V001__payments.sql`
- Create: `educloud-backend/educloud-payment/src/test/java/com/educloud/payment/service/PaymentCallbackServiceTest.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/PaymentService.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentController.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentCallbackController.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/provider/PaymentProvider.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/provider/SandboxPaymentProvider.java`
- Create: `educloud-backend/educloud-payment/src/test/java/com/educloud/payment/messaging/OrderCancelledConsumerTest.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/messaging/OrderCancelledConsumer.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/entity/PaymentRefundEntity.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/entity/PaymentOrderItemSnapshotEntity.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/entity/PaymentRefundItemEntity.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/mapper/PaymentOrderItemSnapshotMapper.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/mapper/PaymentRefundMapper.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/mapper/PaymentRefundItemMapper.java`
- Create: `educloud-backend/educloud-payment/src/test/java/com/educloud/payment/controller/PaymentStatusSnapshotControllerTest.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentStatusSnapshotController.java`

**Steps:**

1. 测试每订单唯一支付聚合、最多一个活动尝试、金额/渠道、签名失败、重复通知、未知交易和成功事件只发布一次；Payment 状态快照只允许 Order 并返回聚合版本。
2. 实现渠道接口和沙箱适配器；生产默认禁用沙箱成功回调。
3. 测试超时关闭/用户取消与成功回调并发、`OrderCancelled` 关闭活动尝试、渠道关闭失败后仍扣款、有效期内事件晚到、过期后扣款、两个历史尝试都成功、并发回调受唯一 `dedup_key` 保护只建一笔自动退款、自动退款失败；V001 包含自动补偿所需 `payment_refund`，保存载荷哈希而非敏感原文日志。
4. 运行 Payment `verify`。
5. 提交 `feat(payment): implement idempotent payment callback`。

### Task 25：支付成功到选课的事件闭环

**Files:**

- Create: `educloud-backend/educloud-order/src/test/java/com/educloud/order/messaging/PaymentSucceededConsumerTest.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/PaymentSucceededConsumer.java`
- Create: `educloud-backend/educloud-order/src/test/java/com/educloud/order/messaging/CancelledPaymentCompensationTest.java`
- Create: `educloud-backend/educloud-payment/src/test/java/com/educloud/payment/messaging/PaymentCompensationRequestedConsumerTest.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/messaging/PaymentCompensationRequestedConsumer.java`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/messaging/OrderPaidConsumerTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/OrderPaidConsumer.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/config/OrderMessagingConfiguration.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/config/CourseMessagingConfiguration.java`

**Steps:**

1. 测试重复或倒序 `PaymentSucceeded` 只更新一次订单并只产生一个 `OrderPaid`；有效期内晚到允许专用 `CLOSED → PAID`，过期后成功不履约；`CANCELLED` 收到成功不履约且只发布一个 `PaymentCompensationRequested`。
2. 测试 Payment 对重复取消补偿只创建一次全额自动退款，订单项分摊关系化保存且合计等于退款额；Order 收到补偿成功只更新取消订单项已退金额、不发布 `OrderPaid`，自动退款失败保留事实并告警。
3. 测试重复/倒序 `OrderPaid`、`EnrollmentCreated/Revoked` 不会重新开放已撤销访问；首条版本大于 1 或运行中缺口分别调用精确 Order fulfillment / Enrollment access 快照校准，再只接受下一连续版本。
4. 测试 Course 暂时失败时消息重试/死信，订单仍保持真实已支付。
5. 运行 Order/Payment/Course 消息集成测试。
6. 提交 `feat(trade): complete paid enrollment flow`。

### Task 26：退款和对账

**Files:**

- Create: `deploy/sql/order/V002__refund_requests.sql`
- Create: `deploy/sql/payment/V002__user_refunds_and_reconciliation.sql`
- Create: `educloud-backend/educloud-order/src/test/java/com/educloud/order/service/RefundRequestServiceTest.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/service/RefundRequestService.java`
- Create: `educloud-backend/educloud-payment/src/test/java/com/educloud/payment/service/PaymentRefundServiceTest.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/PaymentRefundService.java`
- Create: `educloud-backend/educloud-payment/src/test/java/com/educloud/payment/service/ReconciliationServiceTest.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/ReconciliationService.java`
- Create: `educloud-backend/educloud-payment/src/test/java/com/educloud/payment/controller/PaymentRefundSnapshotControllerTest.java`
- Create: `educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentRefundSnapshotController.java`
- Create: `educloud-backend/educloud-order/src/test/java/com/educloud/order/messaging/RefundSucceededConsumerTest.java`
- Create: `educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/RefundSucceededConsumer.java`
- Create: `educloud-backend/educloud-course/src/test/java/com/educloud/course/messaging/OrderRefundedConsumerTest.java`
- Create: `educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/OrderRefundedConsumer.java`

**Steps:**

1. 测试重复/并发退款申请、按稳定顺序锁订单项、`reserved + refunded <= lineAmount`、驳回/取消释放预留、订单项部分退款、人工审核、渠道失败和重复成功。
2. 测试 Payment 的 `RefundSucceeded` 先由 Order 幂等更新订单项并发布带课程范围的 `OrderRefunded`，再由 Course 只撤销对应 Enrollment；验证部分/全部退款状态和历史数据保留。
3. 测试支付成功但订单遗漏、金额差异和人工补偿审计；退款快照只允许 Order，含订单项分摊和 `PaymentRefund` 聚合版本，可恢复首条高版本/运行中缺口。
4. 运行三服务 `verify`。
5. 提交 `feat(trade): add refund and reconciliation`。

### Task 27：交易前端联调

**Files:**

- Modify: `educloud-frontend/student-portal/src/pages/CourseDetail.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/Orders.tsx`
- Modify: `educloud-frontend/student-portal/src/stores/useCartStore.ts`
- Modify: `educloud-frontend/student-portal/src/services/api.ts`
- Create: `educloud-frontend/student-portal/src/components/PaymentResultModal.tsx`
- Modify: `educloud-frontend/admin-portal/src/pages/OrderManage.tsx`
- Modify: `educloud-frontend/admin-portal/src/pages/Finance.tsx`
- Modify: `educloud-frontend/student-portal/src/types/index.ts`
- Modify: `educloud-frontend/admin-portal/src/services/api.ts`
- Modify: `educloud-frontend/admin-portal/src/types/index.ts`

**Steps:**

1. 合并现有工作区差异，先建立订单/支付 DTO 契约测试。
2. 将“立即购买”接到真实购物车/订单，不再直接生成 `PAID`。
3. 接入支付状态轮询或渠道返回页、订单取消和退款申请。
4. 接入管理端订单、财务、退款审核和对账差异；权限不足正确显示。
5. 运行 X-01/X-02、两端 `typecheck/build`，提交 `feat(frontend): connect transaction flow`。

### 阶段 3 检查点

- 支付渠道未确认时只验收 Sandbox 标识和安全边界，不写“真实支付上线”。
- 使用 `requesting-code-review` 审查金额、签名、幂等、对账、选课和退款一致性。

---

## 阶段 4：通知、直播、社区与管理审核

### Task 28：Notification 服务

**Files:**

- Create: `deploy/sql/notification/V001__notifications.sql`
- Create: `educloud-backend/educloud-notification/src/test/java/com/educloud/notification/service/NotificationServiceTest.java`
- Create: `educloud-backend/educloud-notification/src/test/java/com/educloud/notification/messaging/NotificationConsumerTest.java`
- Create: `educloud-backend/educloud-notification/src/test/java/com/educloud/notification/controller/EmailChannelStatusControllerTest.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/entity/NotificationEntity.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/entity/UserNotificationEntity.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/entity/DeliveryTaskEntity.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/mapper/NotificationMapper.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/service/NotificationService.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/controller/NotificationController.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/controller/EmailChannelStatusController.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/DomainNotificationConsumer.java`
- Create: `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/support/DeliveryTaskJob.java`

**Steps:**

1. 测试事件、接收人和模板幂等，已读只影响本人，投递失败不回滚来源业务。
2. 实现站内通知和未读数；邮件投递使用可禁用适配器。
3. 实现脱敏邮件渠道状态和向当前管理员已验证邮箱发送的限频测试邮件；测试无 SMTP 密码出入、权限、审计与错误脱敏。
4. 测试有限重试、最终失败和审计。
5. 运行 Notification `verify`。
6. 提交 `feat(notification): implement inbox and delivery tasks`。

### Task 29：Live 控制面、聊天和回放元数据

**Files:**

- Create: `deploy/sql/live/V001__live_control_plane.sql`
- Create: `educloud-backend/educloud-live/src/test/java/com/educloud/live/service/LiveLifecycleServiceTest.java`
- Create: `educloud-backend/educloud-live/src/test/java/com/educloud/live/websocket/LiveWebSocketSecurityTest.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/LiveRoomEntity.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/LiveSessionEntity.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/mapper/LiveRoomMapper.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/service/LiveLifecycleService.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/controller/LiveRoomController.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/websocket/LiveWebSocketHandler.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/messaging/ReplayFileConsumer.java`
- Create: `educloud-backend/educloud-live/src/test/java/com/educloud/live/controller/ReplayDownloadControllerTest.java`
- Create: `educloud-backend/educloud-live/src/main/java/com/educloud/live/support/FileDownloadGrantClient.java`

**Steps:**

1. 测试教师归属、非法状态迁移、学生选课、一次性连接票据、票据重用、伪造用户 ID、频率/长度和断线重连。
2. 实现 `CREATED→LIVING→ENDED` 和观看记录。
3. 媒体供应商未决定时返回明确控制面状态，不生成虚假流地址。
4. 测试回放状态和 File 越权；回放列表在 Live 授权后每页只调用一次批量 grant，跨课程、退款/撤销选课、伪造绑定和过期地址不可访问。
5. 提交 `feat(live): implement secure live control plane`。

### Task 30：社区与社区对象审核

**Files:**

- Create: `deploy/sql/content/V005__community.sql`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/CommunityServiceTest.java`
- Create: `educloud-backend/educloud-content/src/test/java/com/educloud/content/service/CommunityAuditExtensionTest.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/CommunityController.java`
- Modify: `educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ContentAuditController.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/CommunityPostEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/CommunityCommentEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/CommunityReactionEntity.java`
- Create: `educloud-backend/educloud-content/src/main/java/com/educloud/content/service/CommunityService.java`
- Modify: `educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ContentAuditService.java`
- Modify: `educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/ContentAuditEventPublisher.java`

**Steps:**

1. 测试帖子审核、撤回、帖子/评论权限、点赞收藏唯一、删除审计、拒绝原因必填和禁止自审；回归 Task 16 的内容修订审核不受扩展影响。
2. 在既有 `content_audit_submission` 和审核服务上扩展社区对象目标版本；课程内容修订审核不在本任务重复创建。
3. 确保当前管理端内容驳回原因真正落库。
4. 运行 Content `verify`。
5. 提交 `feat(content): persist community and audits`。

### Task 31：互动前端联调

**Files:**

- Modify: `educloud-frontend/student-portal/src/pages/Notifications.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/Community.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/LiveRoom.tsx`
- Modify: `educloud-frontend/student-portal/src/features/engagement/useCommunityStore.ts`
- Modify: `educloud-frontend/teacher-portal/src/pages/LiveManage.tsx`
- Modify: `educloud-frontend/teacher-portal/src/stores/useLiveStore.ts`
- Modify: `educloud-frontend/admin-portal/src/pages/ContentAudit.tsx`
- Modify: `educloud-frontend/student-portal/src/services/api.ts`
- Modify: `educloud-frontend/student-portal/src/types/index.ts`
- Modify: `educloud-frontend/teacher-portal/src/services/api.ts`
- Modify: `educloud-frontend/teacher-portal/src/types/index.ts`
- Modify: `educloud-frontend/admin-portal/src/services/api.ts`
- Modify: `educloud-frontend/admin-portal/src/types/index.ts`

**Steps:**

1. 先建立通知、社区、直播和审核 DTO 契约测试。
2. 替换 Zustand 会话数据为服务端数据，同时保留乐观 UI 的失败回滚。
3. WebSocket 使用认证握手，断线和直播结束正确收敛。
4. 管理端拒绝原因随 API 提交并显示服务端结果。
5. 运行三端 `typecheck/build` 和相关后端测试，提交 `feat(frontend): connect engagement flows`。

### 阶段 4 检查点

- 真实媒体未接入时，只能交付直播控制面。
- 评审消息身份、社区越权、审核证据和通知失败隔离。

---

## 阶段 5：搜索、分析和推荐

### Task 32：Search 索引和查询

**Files:**

- Create: `deploy/sql/search/V001__index_tasks.sql`
- Create: `educloud-backend/educloud-search/src/test/java/com/educloud/search/messaging/CourseIndexConsumerTest.java`
- Create: `educloud-backend/educloud-search/src/test/java/com/educloud/search/service/SearchServiceTest.java`
- Create: `educloud-backend/educloud-search/src/test/java/com/educloud/search/service/IndexRebuildServiceTest.java`
- Create: `educloud-backend/educloud-search/src/main/resources/elasticsearch/course-v1.json`
- Create: `educloud-backend/educloud-search/src/main/resources/elasticsearch/content-v1.json`
- Create: `educloud-backend/educloud-search/src/main/java/com/educloud/search/messaging/CourseIndexConsumer.java`
- Create: `educloud-backend/educloud-search/src/main/java/com/educloud/search/messaging/ContentIndexConsumer.java`
- Create: `educloud-backend/educloud-search/src/main/java/com/educloud/search/service/SearchService.java`
- Create: `educloud-backend/educloud-search/src/main/java/com/educloud/search/service/IndexRebuildService.java`
- Create: `educloud-backend/educloud-search/src/main/java/com/educloud/search/controller/SearchController.java`
- Create: `educloud-backend/educloud-search/src/main/java/com/educloud/search/entity/IndexTaskEntity.java`

**Steps:**

1. 测试只索引已发布课程/内容、下架删除、重复/倒序事件、索引失败任务和受保护内容字段权限。
2. 实现 `educloud-course-v1`、`educloud-content-v1`、两类查询、别名切换和“已提交 W1 + 快照 + W2 + `(W1,W2]` 归档 + 实时事件”全量重建。
3. 测试 Search 故障不影响 Course 详情。
4. 运行 Elasticsearch 集成测试和 Search `verify`。
5. 提交 `feat(search): add course indexing and query`。

### Task 33：Analytics 聚合和审计读模型

**Files:**

- Create: `deploy/sql/analytics/V001__metrics_and_audit_views.sql`
- Create: `educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/messaging/AnalyticsEventConsumerTest.java`
- Create: `educloud-backend/educloud-analytics/src/test/java/com/educloud/analytics/service/DailyAggregationServiceTest.java`
- Create: `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/TeacherAnalyticsController.java`
- Create: `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/AdminAnalyticsController.java`
- Create: `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/FinanceAnalyticsController.java`
- Create: `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/AuditEventController.java`
- Create: `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/DailyAggregationService.java`
- Create: `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/service/AggregationRebuildService.java`
- Create: `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/AnalyticsEventConsumer.java`

**Steps:**

1. 测试重复/迟到/乱序事件、按日重算、金额来源、统计截止时间，以及 W1/W2 快照窗口内变更不会在全量重建中丢失。
2. 实现教师、平台、财务和审计查询视图；重建写临时表，行数/金额/最大版本校验后原子切换。
3. 验证 Analytics 不能修改来源服务数据。
4. 运行 Analytics `verify`。
5. 提交 `feat(analytics): build metrics and audit views`。

### Task 34：规则推荐和降级

**Files:**

- Create: `deploy/sql/recommendation/V001__rule_recommendations.sql`
- Create: `educloud-backend/educloud-recommendation/src/test/java/com/educloud/recommendation/service/RecommendationServiceTest.java`
- Create: `educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/service/RecommendationService.java`
- Create: `educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/controller/RecommendationController.java`
- Create: `educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/messaging/RecommendationEventConsumer.java`
- Create: `educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/entity/RecommendationRuleConfigEntity.java`
- Create: `educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/entity/RecommendationFeedbackEntity.java`
- Create: `educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/mapper/RecommendationRuleConfigMapper.java`
- Create: `educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/mapper/RecommendationFeedbackMapper.java`

**Steps:**

1. 测试可见课程过滤、规则版本、理由、重复反馈、热门课程降级，以及 W1/W2 快照重建期间事件补齐和聚合版本拒绝倒退。
2. 实现确定性规则排序；固定输入得到固定输出。
3. 不创建模型密钥、向量表或自动 AI 决策。
4. 运行 Recommendation `verify`。
5. 提交 `feat(recommendation): add explainable course ranking`。

### Task 35：管理与派生能力前端联调

**Files:**

- Modify: `educloud-frontend/student-portal/src/pages/Home.tsx`
- Modify: `educloud-frontend/student-portal/src/pages/CourseList.tsx`
- Modify: `educloud-frontend/student-portal/src/services/api.ts`
- Modify: `educloud-frontend/teacher-portal/src/pages/Dashboard.tsx`
- Modify: `educloud-frontend/teacher-portal/src/pages/Analytics.tsx`
- Modify: `educloud-frontend/teacher-portal/src/services/api.ts`
- Modify: `educloud-frontend/admin-portal/src/pages/Dashboard.tsx`
- Modify: `educloud-frontend/admin-portal/src/pages/Finance.tsx`
- Modify: `educloud-frontend/admin-portal/src/pages/Logs.tsx`
- Modify: `educloud-frontend/admin-portal/src/pages/UserManage.tsx`
- Modify: `educloud-frontend/admin-portal/src/pages/SystemConfig.tsx`
- Modify: `educloud-frontend/admin-portal/src/components/ConfigForm.tsx`
- Modify: `educloud-frontend/admin-portal/src/stores/useUserStore.ts`
- Modify: `educloud-frontend/admin-portal/src/stores/useSystemStore.ts`
- Create: `educloud-frontend/admin-portal/.env.example`
- Modify: `educloud-frontend/admin-portal/src/services/api.ts`
- Keep: `educloud-frontend/student-portal/src/features/engagement/assistantClient.ts` 的 Mock/远端状态边界

**Steps:**

1. 接入搜索、推荐、教师统计、管理统计、用户管理、公开平台配置和审计视图。
2. 系统配置页删除 SMTP/MinIO/JWT 原始密钥输入与回显，只显示分项脱敏状态并调用受限连接测试；服务健康卡不调用虚构业务聚合接口，使用非敏感 `VITE_OPERATIONS_DASHBOARD_URL` 跳转受保护的外部 Grafana，未配置时显示“运维入口未配置”而非假状态。
3. 用户页接入分页与禁用/恢复；删除入口移除或明确标记 `【后续规划】`，不得调用不存在的匿名化接口。
4. 显示统计截止时间和派生服务不可用状态。
5. AI 助手未获专项批准时不接内部“假 AI”接口，仍清晰显示演示或外部连接状态。
6. 运行三端 `typecheck/build`、管理端 E2E 和相关后端测试。
7. 提交 `feat(frontend): connect admin and derived capabilities`。

### 阶段 5 检查点

- Search、Analytics、Recommendation 可删除派生数据后重建。
- 故障不阻断登录、课程、学习、订单和支付。
- AI 能力边界在 UI、文档和运行配置中一致。

---

## 阶段 6：生产就绪与最终交付

### Task 36：日志、指标、审计和告警

**Files:**

- Create: 各服务 `config/ObservabilityConfiguration.java`
- Create: `deploy/docker-compose/prometheus/prometheus.yml`
- Create: `deploy/docker-compose/grafana/provisioning/`
- Create: `deploy/runbooks/*.md`
- Create: `deploy/tests/audit-event-permissions.sql`
- Modify: 各服务 `application.yml`

**Steps:**

1. 测试错误响应含 requestId、日志 MDC 清理、敏感字段不输出。
2. 为 HTTP/JVM/MySQL/Redis/RabbitMQ/ES/MinIO 和业务事件暴露指标；验证各服务来源审计表应用账号只有 INSERT/SELECT。
3. 建立初始 Grafana 仪表盘、受保护访问入口和可配置告警；不新建产品侧“服务状态聚合 API”。
4. 验证支付异常、死信、5xx 和依赖故障能被观察。
5. 提交 `feat(ops): add observability and runbooks`。

### Task 37：完整 Compose 联调环境

**Files:**

- Modify: `deploy/docker-compose/compose.yml`
- Create: 每个服务 Dockerfile
- Create: `deploy/scripts/smoke-test.ps1`
- Create: `deploy/scripts/verify-migrations.ps1`

**Steps:**

1. 为所有服务创建非 root、多阶段镜像。
2. Compose 加入后端服务、健康检查、Nacos 配置和独立数据库账号。
3. 从空卷启动，应用所有迁移；从未提交 `.env` 运行服务客户端 `bootstrap` 与 `verify`，确认 User DB 哈希和各服务开发 Secret 对应后，再运行 X-01 至 X-10 冒烟场景。
4. 重启服务验证数据、Outbox/Inbox 和幂等结果。
5. 提交 `chore(deploy): complete local integration stack`。

### Task 38：Kubernetes 与 Helm

**Files:**

- Create: `deploy/kubernetes/base/<service>/deployment.yml`
- Create: `deploy/kubernetes/base/<service>/service.yml`
- Create: `deploy/kubernetes/base/gateway/ingress.yml`
- Create: `deploy/kubernetes/overlays/dev/`
- Create: `deploy/kubernetes/overlays/staging/`
- Create: `deploy/kubernetes/jobs/service-client-bootstrap.yml`
- Create: `deploy/helm/educloud/Chart.yaml`
- Create: `deploy/helm/educloud/values.yaml`

**Steps:**

1. 为每服务配置 startup/liveness/readiness、优雅停机、request/limit、Secret 引用，以及服务端 HTTPS 证书/受信 CA 挂载与轮换；测试 Feign、Gateway 下游和 Token 端点证书校验。
2. Gateway 才能经 Ingress 对外；内部端点不暴露。服务客户端 bootstrap/rotate Job 从 Secret volume 读取凭据，使用最小权限并在完成后清理 Pod。
3. 运行 Helm 模板和 Kubernetes schema 验证。
4. 在可用集群执行滚动发布、未就绪隔离和应用回滚演练。
5. 没有集群时明确标记运行验证未完成，提交 `chore(k8s): add deployment manifests`。

### Task 39：安全、迁移、性能与恢复验收

**Files:**

- Create: `deploy/tests/security-checklist.md`
- Create: `deploy/tests/migration-matrix.md`
- Create: `deploy/tests/performance-scenarios.md`
- Create: `deploy/runbooks/backup-restore.md`
- Create: `deploy/runbooks/payment-reconciliation.md`
- Create: `deploy/runbooks/dead-letter-replay.md`

**Steps:**

1. 执行未认证、越权、跨用户、跨课程、Token 重用、回调伪造、文件越权和 X-09 敏感配置泄露测试；确认前端无删除用户请求且 Secret 只走运维发布链路。
2. 验证每服务空库升级、上一版本升级和补偿/回退路径。
3. 执行关键查询和交易压测，包含 Outbox 单行提交顺序分配器的锁等待与吞吐，记录真实环境、数据规模和 P95/P99；若成为瓶颈，形成专项 ADR，不以乱序 ID 偷换水位语义。
4. 执行 MySQL、MinIO、事件水位和“User DB + Kubernetes 服务凭据 Secret”一致恢复演练；验证不匹配时全量受控轮换与 `tokenVersion` 撤销，记录实际 RTO/RPO，不使用假定值。
5. 提交 `test: document production readiness evidence`。

### Task 40：最终文档与追踪矩阵收口

**Files:**

- Modify: `docs/superpowers/specs/2026-08-18-educloud-current-state-and-gap.md`
- Modify: `docs/superpowers/specs/2026-08-18-educloud-traceability-matrix.md`
- Modify: `docs/superpowers/specs/2026-08-18-educloud-backend-index.md`
- Modify: `educloud-backend/README.md`
- Modify: 与本轮实际实现对应的 `deploy/runbooks/*.md`

**Steps:**

1. 逐行核对页面—API—服务—表—权限—测试证据。
2. 只有实现、迁移、测试、联调和可观测性均有证据时，把状态改为 `【已实现】`。
3. 真实支付、媒体直播和 AI 未完成时保留明确边界。
4. 运行全量后端验证、三端 `typecheck/build`、Compose 冒烟、迁移和 `git diff --check`。
5. 使用 `requesting-code-review` 完成最终架构、安全、数据和交付审查。
6. 提交 `docs: finalize backend delivery evidence`。

---

## 2. 每阶段统一验收

阶段完成必须同时满足：

- 实现代码、迁移、API/事件文档齐全。
- 单元、Web/安全、数据、消息和关键业务测试通过。
- 对应页面从 Mock 切换到真实 Gateway 接口。
- 相关前端 `typecheck/build` 通过。
- 日志、指标、审计和健康检查可验证。
- 失败、重复、并发、超时和补偿路径有证据。
- 追踪矩阵更新，未完成能力仍明确标注。

## 3. 最终验证命令

```powershell
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$repoRoot = (Get-Location).Path

mvn -f educloud-backend/pom.xml verify
docker compose -f deploy/docker-compose/compose.yml config
& ./deploy/tests/migration-runner-tests.ps1
& ./deploy/tests/technical-table-contract-tests.ps1

try {
    docker compose -f deploy/docker-compose/compose.yml up -d --wait
    & ./deploy/scripts/verify-migrations.ps1
    & ./deploy/scripts/bootstrap-service-clients.ps1 -Mode verify
    & ./deploy/scripts/smoke-test.ps1

    Set-Location educloud-frontend/student-portal
    pnpm install --frozen-lockfile
    pnpm run typecheck
    pnpm run build

    Set-Location ../teacher-portal
    npm ci
    npm run typecheck
    npm run build

    Set-Location ../admin-portal
    npm ci
    npm run typecheck
    npm run build

    Set-Location ../e2e
    npm ci
    npx playwright test

    Set-Location -LiteralPath $repoRoot
    helm lint deploy/helm/educloud
    helm template educloud deploy/helm/educloud | kubectl apply --dry-run=client -f -
}
finally {
    Set-Location -LiteralPath $repoRoot
    docker compose -f deploy/docker-compose/compose.yml down
}

git diff --check
git status --short
```

Docker、Playwright 浏览器、Helm/kubectl、Kubernetes 集群、支付沙箱或外部媒体环境不可用时，逐条列出未执行命令、阻塞原因和剩余风险；`compose config`、Helm 渲染或 `kubectl --dry-run=client` 不能替代真实 Compose smoke、浏览器 E2E 或集群滚动/回滚证据。Compose 启动后即使中途失败也必须在受控清理步骤执行不带 `-v` 的 `down`，保留数据卷用于诊断。

## 4. 执行方式

推荐按阶段在独立 worktree 中执行本计划。每个阶段结束后停下评审，确认测试、边界和未完成项，再进入下一阶段。不要一次性生成全部服务后才联调。
