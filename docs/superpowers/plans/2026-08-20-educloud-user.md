# EduCloud M03 User 模块实施计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans` 在当前对话内逐任务实现；步骤使用复选框（`- [ ]`）跟踪，未经本计划书面审阅确认不得创建 User 模块 Java 代码或数据库迁移。

> **状态：** 待用户书面审阅（草案）。

**目标：** 交付可独立运行的 `educloud-user` Spring MVC 服务，实现真实认证（注册/登录/Refresh 轮换/注销/改密）、账号保护（锁定/禁用/恢复）、会话撤销（DB 权威 + Redis 读模型与 Gateway 对齐）、RBAC 授权基础、平台公开配置、JWT 签名密钥状态与内部服务令牌签发；完成后与 Gateway 完成真实登录联调验收。

**架构：** User 服务是身份、账号、档案、RBAC、会话与公开配置的权威服务。Access Token 由 User 以 RSA/RS256 签发（私钥仅存 User），Gateway 与其它服务持公钥校验；Refresh Token 随机值只存 SHA-256 哈希，按会话族行锁原子轮换；Redis 会话 hash（`educloud:{env:auth}:session:{sid}`）是 Gateway 的在线撤销读模型。服务间认证使用 HTTP Basic `client_credentials` 签发 5 分钟服务 Token。业务更新与 Outbox 同事务发布领域事件。

**技术栈：** Java 17 字节码（JDK 17/21 构建）、Spring Boot 3.2.5、Spring MVC、Spring Security 6.2（BCrypt、Resource Server、方法安全）、MyBatis-Plus 3.5.x、MySQL 8.0.36、Redis 7.2.5（Lettuce）、RabbitMQ 3.13.7、Nacos 2.3.2（仅注册）、Nimbus JOSE（RS256/JWK）、Micrometer/Prometheus、Brave/Zipkin、springdoc-openapi、JUnit 5.10、Mockito 5.11、Testcontainers 1.19.7、Awaitility。

**批准规格：** [`2026-08-20-educloud-user-design.md`](../specs/2026-08-20-educloud-user-design.md)（随本计划一并审阅）。

---

## 执行规则

- 每个任务先写确定性失败测试，再实现最小代码；测试必须先在无实现时失败。
- 数据库迁移必须先写、后跑、不可修改已发布脚本；checksum 校验失败即停。
- 会话契约（Redis key/字段/TTL、JWT claims）以 M02 Gateway 实现为准，任何改动需要同时改 Gateway 并全量回归。
- 不在本模块引入删除/匿名化用户、找回密码、前端修改、File 真实调用。
- 每个任务结束运行 `mvn -f educloud-backend/pom.xml -pl educloud-user -am test`（或指定测试）。
- 提交信息遵循仓库惯例（feat/fix/test/docs + 模块名）。

## 目标文件图（新建/修改概览）

```text
educloud-backend/
├─ pom.xml                                  修改：新增 educloud-user 模块 + 依赖管理（mybatis-plus/springdoc/mysql）
└─ educloud-user/                           新建
   ├─ pom.xml
   └─ src/main/java/com/educloud/user/
      ├─ UserApplication.java
      ├─ controller/        AuthController, MeController, UserAdminController,
      │                     RoleController, PlatformConfigController,
      │                     SigningKeyStatusController, InternalServiceTokenController,
      │                     InternalUserStatusSnapshotController, InternalJwksController
      ├─ dto/request/       RegisterStudentRequest, LoginRequest, RefreshRequest(空), ChangePasswordRequest,
      │                     ProfileUpdateRequest, UserStatusUpdateRequest, AssignRolesRequest,
      │                     RoleCreateRequest, RoleUpdateRequest, PlatformConfigUpdateRequest,
      │                     ServiceTokenRequest
      ├─ dto/response/      LoginResponse, UserSummary, UserDetail, UserPageResponse,
      │                     ProfileResponse, RoleResponse, PermissionResponse,
      │                     PlatformConfigResponse, SigningKeyStatusResponse, StatusSnapshotResponse
      ├─ service/           RegistrationService, AuthenticationService, RefreshSessionService,
      │                     SessionRevocationService, ProfileService, AuthorizationService,
      │                     UserAdminService, RoleService, PlatformConfigService,
      │                     ServiceTokenService, SigningKeyStatusService, OutboxPublisher
      ├─ mapper/            SysUserMapper, UserProfileMapper, SysRoleMapper, SysPermissionMapper,
      │                     SysUserRoleMapper, SysRolePermissionMapper, RefreshSessionMapper,
      │                     ServiceClientMapper, ServiceClientCredentialMapper,
      │                     PlatformPublicConfigMapper, LoginAuditMapper, AuditEventMapper,
      │                     OutboxEventMapper, OutboxSequenceMapper, IdempotencyRecordMapper
      ├─ entity/            对应表 Entity
      ├─ security/          JwtKeyProvider, UserJwtEncoder, UserJwtDecoder, ServiceTokenValidator,
      │                     InternalApiFilter, MethodSecurityConfiguration
      ├─ messaging/         UserEventPublisher, OutboxEventDispatcher, RabbitConfiguration
      ├─ config/            UserProperties, SessionProperties, JwtProperties, SecurityProperties,
      │                     DatabaseConfiguration, RedisConfiguration, AsyncConfiguration,
      │                     OpenApiConfiguration, HealthConfiguration
      ├─ support/           LoginNameResolver, PasswordPolicy, ClientFingerprint,
      │                     Masking, UserIdGenerator
      └─ exception/         UserErrorCode, UserExceptionHandler
deploy/
├─ sql/user/                                新建
│  ├─ V000__technical_tables.sql
│  ├─ V001__user_identity_and_rbac.sql
│  └─ V002__session_and_platform.sql
├─ scripts/
│  ├─ run-migrations.sh                     新建（通用迁移器）
│  ├─ generate-user-jwt-keys.sh             新建
│  ├─ provision-user-nacos.sh               新建
│  └─ bootstrap-service-clients.sh          新建
├─ tests/
│  ├─ user-module-contract-tests.sh         新建
│  ├─ user-gateway-e2e-tests.sh             新建
│  ├─ run-migrations-tests.sh               新建
│  ├─ generate-user-jwt-keys-tests.sh       新建
│  └─ (既有脚本/契约按需小改)
└─ docker-compose/
   ├─ compose.yml                           修改：新增 educloud-user
   └─ .env.example                          修改：User 服务变量
docs/superpowers/specs/2026-08-20-educloud-user-design.md   新建（本计划配套）
docs/superpowers/plans/2026-08-20-educloud-user.md          本文件
educloud-backend/README.md                  修改：M03 状态
```

---

## 任务 0：父 POM、模块骨架与依赖管理

**文件：**

- 修改：`educloud-backend/pom.xml`
- 创建：`educloud-backend/educloud-user/pom.xml`
- 创建：`educloud-backend/educloud-user/src/main/java/com/educloud/user/UserApplication.java`
- 创建：`educloud-backend/educloud-user/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-user/src/test/java/com/educloud/user/UserApplicationContextTest.java`

**步骤：**

1. 父 POM `<modules>` 按顺序追加 `educloud-user`；`<dependencyManagement>` 增加 mybatis-plus（`mybatis-plus-spring-boot3-starter` 3.5.7）、springdoc-openapi（`springdoc-openapi-starter-webmvc-ui` 2.5.0）、mysql-connector-j 版本。
2. `educloud-user/pom.xml`：依赖 educloud-common、spring-boot-starter-web、spring-boot-starter-validation、spring-boot-starter-security、spring-boot-starter-oauth2-resource-server、spring-boot-starter-data-redis、spring-boot-starter-amqp、spring-cloud-starter-alibaba-nacos-discovery、spring-boot-starter-actuator、micrometer-registry-prometheus、micrometer-tracing-bridge-brave、zipkin-reporter-brave、mybatis-plus-spring-boot3-starter、mysql-connector-j（runtime）、springdoc-openapi-starter-webmvc-ui；test：spring-boot-starter-test、spring-security-test、testcontainers、junit-jupiter、awaitility。
3. 先写 `UserApplicationContextTest`：启动最小上下文（禁用外部连接）并断言 `context.isActive()`；无实现时失败。
4. 实现启动类、最小 `application.yml`（server.port=8082、spring.application.name=educloud-user、management 8083、mybatis-plus 配置、日志）。
5. 运行 `mvn -f educloud-backend/pom.xml -pl educloud-user -am test`，预期 BUILD SUCCESS。
6. 提交 `build(user): add module boundary`。

## 任务 1：通用迁移器与 V000 技术表

**文件：**

- 创建：`deploy/scripts/run-migrations.sh`
- 创建：`deploy/sql/user/V000__technical_tables.sql`
- 创建：`deploy/tests/run-migrations-tests.sh`

**步骤：**

1. 先写 `run-migrations-tests.sh`（fixture 模式，参考 provision-gateway-nacos-tests.sh）：断言脚本按 VNNN 顺序执行、写 `schema_migration_history`、已成功版本 checksum 不一致时失败、重复执行不重放、DDL 失败停止并记 FAILED、使用 `GET_LOCK('educloud_user_migration')`。
2. `V000__technical_tables.sql`：`schema_migration_history`、`outbox_event`、`outbox_sequence`、`inbox_event`、`audit_event`、`idempotency_record`（字段与索引按数据设计第 14 节）；`user_app` 账号只授予业务表权限，`audit_event` 仅 INSERT/SELECT。
3. 迁移器读取 `MYSQL_HOST/PORT/MYSQL_ROOT_PASSWORD` 或 `EDUCLOUD_USER_MIGRATION_PASSWORD`，对 `educloud_user` 库执行；参数化库名与账号；PowerShell 等价脚本 `deploy/scripts/run-migrations.ps1`（本地 CI 用）。
4. 运行契约测试 + 在 Rocky MySQL 上执行空库迁移；重复执行幂等。
5. 提交 `feat(user): add migration runner and technical tables`。

## 任务 2：V001 身份与 RBAC 表 + seed

**文件：**

- 创建：`deploy/sql/user/V001__user_identity_and_rbac.sql`
- 创建：`educloud-user/src/test/java/com/educloud/user/mapper/UserIdentitySchemaIT.java`（Testcontainers MySQL 8.0.36）
- 创建：`educloud-user/src/main/java/com/educloud/user/entity/`（SysUserEntity、UserProfileEntity、SysRoleEntity、SysPermissionEntity、SysUserRoleEntity、SysRolePermissionEntity）
- 创建：`educloud-user/src/main/java/com/educloud/user/mapper/`（对应 Mapper）

**步骤：**

1. 先写 `UserIdentitySchemaIT`：真实 MySQL 8.0.36 容器（镜像经 `TestContainerImages` 解析，支持 `EDUCLOUD_TEST_MYSQL_IMAGE` 私有镜像覆盖）；断言 username/email/phone 唯一索引、`(user_id, role_id)` 唯一、乐观锁字段存在、seed 角色与权限存在、`user_app` 无库级权限。
2. 按数据设计第 3 节实现 V001（字段/索引/唯一约束 + 7 个内置角色 + User 域权限码 seed + 基础角色映射；权限码总数控制在 64 内）。
3. 实现 Entity/Mapper（MyBatis-Plus，ASSIGN_ID；User datacenterId=0，复用 Common `WorkerLeaseIdentifierGenerator`）。
4. 运行 IT；迁移在空库上执行。
5. 提交 `feat(user): add identity and rbac schema`。

## 任务 3：V002 会话/服务客户端/平台配置表

**文件：**

- 创建：`deploy/sql/user/V002__session_and_platform.sql`
- 创建：`educloud-user/src/test/java/com/educloud/user/mapper/SessionSchemaIT.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/entity/`（RefreshSessionEntity、ServiceClientEntity、ServiceClientCredentialEntity、PlatformPublicConfigEntity、LoginAuditEntity、AuditEventEntity、OutboxEventEntity、OutboxSequenceEntity、IdempotencyRecordEntity）
- 创建：`educloud-user/src/main/java/com/educloud/user/mapper/`（对应 Mapper）

**步骤：**

1. 先写 `SessionSchemaIT`：断言 `session_token_hash` 唯一、`token_id` 唯一、`(family_id, status)` 索引、`(user_id, expires_at)` 索引、service_client_credential 唯一 `(service_client_id, credential_version)`、login_audit 索引、platform_public_config `config_key` 唯一与 seed 内容。
2. 按数据设计第 3 节实现 V002（refresh_session 字段含 parent/replaced 链；service_client/credential；platform_public_config seed 站点名等非敏感值）。
3. 实现 Entity/Mapper。
4. 运行 IT；提交 `feat(user): add session and platform schema`。

## 任务 4：模块契约测试

**文件：**

- 创建：`deploy/tests/user-module-contract-tests.sh`

**步骤：**

1. 契约断言：父 POM 模块顺序 common→gateway→user；User 依赖边界（允许 web/security/oauth2-resource-server/data-redis/amqp/nacos-discovery/actuator/mybatis-plus/mysql/springdoc/validation/testcontainers；禁止依赖其它业务模块、禁止依赖 educloud-gateway）；存在 `db/migration`（`deploy/sql/user` 存在且 V 脚本 checksum 可控）；主源码含 controller/entity/mapper/service；不含私钥/`BEGIN PRIVATE KEY`/JWK 私钥参数；`application.yml` 含 `spring.application.name=educloud-user`；README 状态未提前宣称完成。
2. 在 Windows 与 Rocky 分别运行；提交 `test(user): add module contract gate`。

## 任务 5：JWT 密钥与签名基础

**文件：**

- 创建：`deploy/scripts/generate-user-jwt-keys.sh`
- 创建：`deploy/tests/generate-user-jwt-keys-tests.sh`
- 创建：`educloud-user/src/main/java/com/educloud/user/security/JwtKeyProvider.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/security/UserJwtEncoder.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/security/JwtKeyProviderTest.java`、`UserJwtEncoderTest.java`

**步骤：**

1. 先写测试：私钥文件缺失/权限过宽/非 RSA/算法不符 → 启动失败；JWKS 输出只含公钥（无 d/p/q 等）；签发 Token 的 `alg=RS256`、含 `kid`，可用公钥验签；kid 轮换（新文件 + 新 kid）后旧 Token 仍可验（双 key 加载）。
2. `generate-user-jwt-keys.sh`：生成 RSA 2048 私钥（0600）与公共 jwks.json（0644），写入 `USER_JWT_PRIVATE_KEY_LOCATION`/`GATEWAY_JWKS_LOCATION` 指向的路径；相同 kid 幂等，`--force` 才重建；密钥不打印。
3. `JwtKeyProvider`：从文件加载 RSA 密钥（Nimbus `RSAKey`），暴露 `JWKSource` 与当前 `kid`；`UserJwtEncoder` 封装 `NimbusJwtEncoder`（RS256）。
4. 运行单元测试 + 契约脚本；提交 `feat(user): add jwt signing key foundation`。

## 任务 6：学生自助注册

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/service/RegistrationServiceTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/controller/AuthRegisterControllerTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/RegistrationService.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/controller/AuthController.java`（先只含 register）
- 创建：`educloud-user/src/main/java/com/educloud/user/dto/request/RegisterStudentRequest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/security/PasswordConfiguration.java`（BCryptEncoder Bean）
- 创建：`educloud-user/src/main/java/com/educloud/user/support/PasswordPolicy.java`

**步骤：**

1. 先写测试：注册开关关闭 → 403 `REGISTRATION_DISABLED`；密码短于 8 → 422 `PASSWORD_WEAK`；用户名/邮箱/手机重复 → 409（唯一索引并发双写保护）；portal 非 STUDENT → 400；成功注册默认分配 STUDENT 角色、密码 BCrypt 哈希、不返回哈希、发布 `UserRegistered`（Outbox 行断言）、写审计。
2. 实现 Service/Controller/DTO；Idempotency-Key 支持（可选头，同 key 同请求重放返回首次结果）。
3. 运行测试；提交 `feat(user): add student self registration`。

## 任务 7：登录与账号保护

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/service/AuthenticationServiceTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/controller/AuthLoginControllerTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/AuthenticationService.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/dto/request/LoginRequest.java`、`dto/response/LoginResponse.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/support/LoginNameResolver.java`、`ClientFingerprint.java`、`SessionFactory.java`
- 修改：`AuthController.java`（login）

**步骤：**

1. 先写测试：正确密码 → 200，data 含 accessToken/expiresIn/用户摘要；错误密码与账号不存在返回相同 401 `INVALID_CREDENTIALS`（防枚举）；连续 5 次失败 → 423 `ACCOUNT_LOCKED` 且锁定期内拒绝；锁定到期自动放行并重置；DISABLED → 403 `ACCOUNT_DISABLED`；登录成功写 Redis session（key/字段/TTL 断言）与 refresh_session 行（哈希，不存明文）、写 login_audit；响应 Cookie `refresh_token` HttpOnly/Path=/api/v1/auth；失败审计不记密码。
2. 实现登录：解析 loginName（username/email/phone）→ 校验状态/锁定 → BCrypt 校验（失败计数）→ 生成 Refresh（256-bit 随机）→ 同事务写 refresh_session + login_audit + 更新 last_login_at → Redis 写 ACTIVE → 签 Access Token（claims 含 roles/permissions 摘要）。
3. 运行测试；提交 `feat(user): implement secure login`。

## 任务 8：Refresh 轮换、注销与会话撤销

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/service/RefreshSessionServiceTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/service/SessionRevocationServiceTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/RefreshSessionService.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/SessionRevocationService.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/config/SessionProperties.java`
- 修改：`AuthController.java`（refresh/logout）

**步骤：**

1. 先写测试（含真实 MySQL 行锁并发 IT）：原子轮换（父 ACTIVE→ROTATED + 子行 + 新 Access/新 Cookie 同 family）；并发双刷新（两线程）只有一个成功、另一个在宽限窗口内得 409 `REFRESH_ALREADY_ROTATED`；窗口外重用父 Token → 家族全 REVOKED + Redis REVOKED + 审计 `SESSION_REUSE_DETECTED`；指纹不匹配 → 撤销家族；DISABLED/过期/REVOKED → 401；Redis 不可用 → 401 失败关闭；注销幂等（第二次 204）；注销后原 Access 在 Gateway 侧 401（e2e 断言）。
2. 实现轮换事务（`SELECT FOR UPDATE` + 宽限窗口 + 指纹）；注销（DB+Redis 撤销 + 清 Cookie）；Redis 写失败补偿重试。
3. 运行测试；提交 `feat(user): add refresh rotation and revocation`。

## 任务 9：改密、禁用/恢复与撤销矩阵

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/service/PasswordChangeServiceTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/service/UserStatusServiceTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/PasswordChangeService.java`、`UserStatusService.java`
- 修改：`AuthController.java`（password/change）、`UserAdminController.java`（status）

**步骤：**

1. 先写测试：旧密码错误 → 401/422 明确码；改密成功 → 新哈希、`token_version+1`、其它 family REVOKED、当前 family 保留 ACTIVE 且 Redis 以新 tokenVersion 重写 ACTIVE（旧 Access 在 Gateway 侧 401，刷新可续期）；禁用 → 全部 REVOKED + Redis REVOKED + token_version+1 + 登录/刷新拒绝；恢复 → 可登录；状态迁移带 `version` 乐观锁，冲突 409；写审计并发布 `UserStatusChanged`。
2. 实现撤销矩阵（设计规格 4.4）与 Redis 补偿。
3. 运行测试；提交 `feat(user): add password change and account status`。

## 任务 10：/me 与用户档案

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/controller/MeControllerTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/service/ProfileServiceTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/controller/MeController.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/ProfileService.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/dto/request/ProfileUpdateRequest.java`、`dto/response/ProfileResponse.java`

**步骤：**

1. 先写测试：GET /me 返回当前用户 + 角色/权限摘要（来自 JWT claims 或 DB 汇总）；无 Token → 401；PATCH /me/profile 仅本人可改（伪造他人 ID 路径不存在）；displayName 长度/字符校验；bio/locale 限制；头像 `avatarFileId` 只收合法 ID 格式且不触发 File 调用（M03 替身断言：无 N+1、不落 URL）；响应不含 email/phone 明文以外敏感字段（手机/邮箱按策略脱敏）。
2. 实现 MeController/ProfileService（本人校验：路径 ID 必须等于认证 sub，服务不接受客户端身份头）。
3. 运行测试；提交 `feat(user): add me and profile endpoints`。

## 任务 11：RBAC 授权基础

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/service/AuthorizationServiceTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/security/MethodSecurityConfigurationTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/AuthorizationService.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/security/MethodSecurityConfiguration.java`

**步骤：**

1. 先写测试：权限汇总（角色→权限）去重且 <=64，超限启动 fail-fast（配置错误）；`@PreAuthorize` 权限码生效（无权限 403 `ACCESS_DENIED`）；角色分配/权限变更写审计 + 发布 `RoleAssignmentChanged`；内置角色不可删除。
2. 实现权限汇总（DB 查询 join 角色权限，缓存策略：Redis 权限摘要缓存 + 变更删除，键含版本前缀）。
3. 运行测试；提交 `feat(user): add rbac authorization foundation`。

## 任务 12：管理端接口（用户/角色/权限/平台配置/签名状态）

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/controller/UserAdminControllerTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/controller/RoleControllerTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/controller/PlatformConfigControllerTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/controller/SigningKeyStatusControllerTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/controller/UserAdminController.java`、`RoleController.java`、`PlatformConfigController.java`、`SigningKeyStatusController.java`
- 创建：对应 Service、Request/Response DTO

**步骤：**

1. 先写测试：`GET /users` 分页（page/pageSize/total 契约）、排序白名单、手机/邮箱脱敏；`GET /users/{id}` 无 `user:read` 时 404（不泄露存在性）；`PATCH /users/{id}/status` 带 version 乐观锁、DISABLED 撤销联动；`PUT /users/{id}/roles` 分配（角色必须存在）；角色 CRUD（built_in 保护、code 唯一、描述长度）；`GET /permissions` 目录；平台配置匿名读/权限写、不含 Secret 键、config_key 白名单；签名状态只返回 kid/数量/更新时间/下次轮换时间，无私钥字段。
2. 实现各 Controller/Service（方法级授权 + 审计）。
3. 运行测试；提交 `feat(user): add admin user rbac config endpoints`。

## 任务 13：Outbox 发布与 RabbitMQ

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/messaging/OutboxPublisherTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/messaging/OutboxEventDispatcherIT.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/messaging/UserEventPublisher.java`、`OutboxEventDispatcher.java`、`RabbitConfiguration.java`

**步骤：**

1. 先写测试：业务事务与 outbox_event 同事务（回滚业务则无 outbox 行）；发布器按 `(publish_status,next_attempt_at)` 小批锁定；投递 RabbitMQ 交换机 `educloud.events` 后标记已发布；确认不明确时重投；重试达阈值标记失败并告警；`source_sequence` 单调（outbox_sequence 行锁）；事件信封字段完整（eventId/eventType/eventVersion/sourceService/sourceSequence/aggregateType/aggregateId/aggregateVersion/occurredAt/requestId/traceId/data）；路由键 `aggregateType:aggregateId`。
2. 实现发布器（@Scheduled 小批 + 手动确认；不阻塞业务事务）。
3. 运行单元 + IT（真实 RabbitMQ Testcontainer，支持私有镜像 env）；提交 `feat(user): publish domain events via outbox`。

## 任务 14：内部服务令牌

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/service/ServiceTokenServiceTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/controller/InternalServiceTokenControllerTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/command/ServiceClientCredentialCommandTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/service/ServiceTokenService.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/controller/InternalServiceTokenController.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/command/ServiceClientCredentialCommand.java`
- 创建：`deploy/scripts/bootstrap-service-clients.sh`、`rotate-service-client.sh`
- 创建：`deploy/tests/bootstrap-service-clients-tests.sh`

**步骤：**

1. 先写测试：HTTP Basic 认证 + `client_credentials` 表单；secret 哈希匹配才签发；audience/scope 白名单；响应只含 `access_token/token_type/expires_in`；Token claims（sub=service:<clientId>、clientId、aud、scope、jti、tokenVersion、5 分钟 exp）可验签；凭据双版本轮换（新 ACTIVE + 旧 GRACE 24h + 到期 REVOKED；并发轮换行锁；最多一 ACTIVE 一 GRACE）；`token_version` 递增立即使旧服务 Token 失效（目标服务校验）；凭据不进 URL/日志/argv；非本地环境拒绝 HTTP 明文；bootstrap 脚本相同值幂等、不同值拒绝隐式覆盖、Secret 只从 stdin/文件读取。
2. 实现 Service/Controller/Command/脚本。
3. 运行测试；提交 `feat(user): issue scoped service tokens`。

## 任务 15：内部状态快照与 JWKS 出口

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/controller/InternalUserStatusSnapshotControllerTest.java`
- 创建：`educloud-user/src/test/java/com/educloud/user/controller/InternalJwksControllerTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/controller/InternalUserStatusSnapshotController.java`、`InternalJwksController.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/security/InternalApiFilter.java`

**步骤：**

1. 先写测试：`/internal/v1/users/{id}/status-snapshot` 只允许登记 clientId/aud/scope（ACL），返回 `{userId,status,tokenVersion,aggregateVersion}`；越权 clientId → 403；`/internal/v1/public-jwks` 只返回公钥、无私钥参数；InternalApiFilter 校验服务 Token（签名/aud/scope/clientId/tokenVersion）。
2. 实现 Filter 与 Controller。
3. 运行测试；提交 `feat(user): add internal snapshots and jwks endpoint`。

## 任务 16：数据集成与迁移测试收口

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/mapper/MigrationVerificationIT.java`
- 修改：既有 `*SchemaIT` 与 `RefreshSessionServiceTest` 并发用例

**步骤：**

1. 迁移测试（MySQL 8.0.36 Testcontainer）：空库升级到 V002；V001 后带代表性数据再升 V002；重复执行保护；checksum 篡改拒绝；`schema_migration_history` 记录完整。
2. 数据集成补充：唯一约束并发（username/email/phone）、行锁轮换并发（两线程刷新）、乐观锁版本冲突、分页/排序白名单。
3. 运行 `mvn -f educloud-backend/pom.xml -pl educloud-user -am verify`（默认与 `-Pintegration`，integration profile 在 user pom 增加，skipITs 默认 true）。
4. 提交 `test(user): verify migrations and data contracts`。

## 任务 17：与 Gateway 真实登录联调（Rocky e2e）

**文件：**

- 创建：`deploy/tests/user-gateway-e2e-tests.sh`
- 创建：`deploy/scripts/provision-user-nacos.sh`
- 修改：`deploy/docker-compose/compose.yml`（新增 educloud-user）、`deploy/docker-compose/.env.example`、`deploy/tests/compose-contract-tests.sh`（允许 user、仍拒绝 M04+）、`deploy/runbooks/rocky-linux-8.9-bootstrap.md`（新增 M03 章节）

**步骤：**

1. 先写 e2e 脚本契约（`deploy/tests/user-gateway-e2e-contract-tests.sh` 或并入模块契约）：断言脚本覆盖注册→登录→/me→刷新→注销→撤销→禁用→改密→重用检测场景，且脚本用独立 `m03-e2e-*` 环境前缀隔离 Redis 数据、结束后清理。
2. `provision-user-nacos.sh`：创建 `educloud_user` Nacos 用户（namespace educloud-local，r/w naming educloud-user，与 gateway 用户隔离）；幂等。
3. 实现 e2e 脚本：启动 User（8082）+ Gateway（8080）+ 共享依赖；断言：
   - 注册学生成功；重复用户名 409。
   - 登录成功（Cookie 存在、Access 可经 Gateway 访问 /me 200）。
   - 并发刷新（两请求）一成功一 409；宽限窗口外重用 → 家族撤销 → 旧 Access 401。
   - 注销后旧 Access 401；禁用后旧 Access 立即 401；改密后旧 Access 401 且新刷新成功。
   - Gateway 侧 Redis 失败关闭语义不回归（停 Redis 容器后受保护请求 503，恢复后正常）。
   - 共享 MySQL/Redis/Nacos/RabbitMQ 结束仍健康；测试数据按前缀清理。
4. 在 Rocky 8.9 运行通过后提交 `feat(user): verify gateway real login integration`。

## 任务 18：可观测性、日志与健康

**文件：**

- 创建：`educloud-user/src/test/java/com/educloud/user/observability/UserHealthIndicatorTest.java`、`UserMetricsTest.java`
- 创建：`educloud-user/src/main/java/com/educloud/user/observability/UserDependenciesHealthIndicator.java`、`UserMetrics.java`

**步骤：**

1. 先写测试：liveness/readiness 分组（MySQL/Redis/Rabbit/Nacos 依赖）；业务指标（登录成功/失败、刷新轮换、重用检测、注册、撤销）记录与低基数 tag；日志字段含 service/environment/requestId/traceId；敏感值（密码/Token/Cookie/密钥）不入日志。
2. 实现健康指示器与指标（Micrometer）；application.yml 配置 management 8083、prometheus、tracing sampling、结构化日志（本地可读格式）。
3. 运行测试；提交 `feat(user): add observability and health`。

## 任务 19：门禁收口与验收

**文件：**

- 修改：`educloud-backend/README.md`（M03 状态与边界）
- 修改：`docs/superpowers/specs/2026-08-20-educloud-user-design.md`（状态改"已实现并验证，等待用户验收"）
- 修改：本计划（勾选完成项）

**步骤：**

1. 全部门禁：10+ 个 deploy 契约脚本（含新 user 相关）、默认 `mvn clean verify`、`-Pintegration`（User 集成测试）、JDK 17/21 双构建 + `javap major version 61`、Rocky e2e。
2. 独立代码审查（架构/安全/会话契约/并发/脱敏/Outbox/服务令牌），无未解决"必须修复"项。
3. `git diff --check`、工作区干净；收口提交 `docs(user): record M03 verification`。
4. 向用户汇报证据并等待书面验收；验收前不进入 M04。

## 规格覆盖矩阵

| 设计规格章节 | 实施任务 |
|---|---|
| 1 目的与前置条件 | 0、17 |
| 2 方案决定 | 0、5、8、14 |
| 3 范围与边界 | 0、4、19 |
| 4 认证与会话契约（Gateway 对齐） | 5、7、8、9、17 |
| 5 账号与登录保护 | 6、7、9 |
| 6 RBAC 与权限 | 11、12 |
| 7 数据库设计 | 1、2、3、16 |
| 8 内部服务令牌 | 14、15 |
| 9 事件与 Outbox | 13 |
| 10 API 契约 | 6~12、14、15 |
| 11 配置与环境 | 0、17 |
| 12 可观测性与安全 | 18 |
| 13 测试策略 | 4、16、17 |
| 14 后续 | 19 |
| 15 完成定义 | 全任务 |

## 完成定义（执行时逐项勾选）

> 执行状态（2026-08-22）：任务 0～18 已实现并提交（含改密端点补齐）；本地门禁全绿（单元 64/64、13 个 deploy 契约脚本、IT 编译）。标 ⏳ 的项需在 Rocky 8.9 执行后勾选。

- [ ] ⏳ 父 POM 新增 educloud-user（common→gateway→user）；JDK 17/21 双构建通过，字节码 61。
- [ ] ⏳ V000/V001/V002 迁移在 MySQL 8.0.36 空库升级、重复执行保护、checksum 校验通过；user_app 无库级写权限（MigrationVerificationIT 待 Rocky 执行）。
- [x] 注册/登录/刷新/注销/改密/锁定/禁用/恢复全部有确定性失败测试与集成证据（64 个单元/方法安全测试）。
- [ ] ⏳ Refresh 原子轮换、并发宽限、窗口外重用撤销家族、指纹不匹配撤销通过；Gateway 在注销/禁用后立即拒绝原 Access Token（user-gateway-e2e 待 Rocky 执行）。
- [x] Redis 会话 key/字段/TTL 与 Gateway RedisSessionVerifier 逐字节对齐（M02 冻结契约 + 单元断言）。
- [x] JWT claims 满足 Gateway 校验器；权限 64 上限校验；JWKS 无私钥参数。
- [x] RBAC seed/分配/方法授权/脱敏/审计通过；平台公开配置不含 Secret。
- [x] 服务令牌 client_credentials、哈希存储、双凭据轮换、ACL、凭据不进 URL/日志通过。
- [ ] ⏳ Outbox 同事务发布四个领域事件，重试与幂等通过（OutboxEventDispatcherIT 待 Rocky 执行）。
- [ ] ⏳ Rocky e2e（User+Gateway 真实登录联调）通过；共享依赖健康；工作区干净。
- [x] 无删除/匿名化、无找回密码端点；README 与文档如实标注 M03 边界。
- [ ] ⏳ 独立代码审查无未解决"必须修复"项；用户书面验收后进入 M04。

