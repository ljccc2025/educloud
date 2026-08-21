# EduCloud M03 User 模块设计规格

> 日期：2026-08-20
>
> 状态：已实现并验证，等待用户验收
>
> 模块：M03 `educloud-user`
>
> 交付类型：可独立运行的 Spring MVC 业务服务（身份、账号、档案、RBAC、会话与平台公开配置权威）

## 1. 目的与前置条件

M03 交付真实认证、会话与授权基础：学生自助注册、登录、Refresh 轮换、注销、改密、账号锁定/禁用/恢复、用户档案、RBAC 角色权限、平台公开配置、JWT 签名密钥状态，以及内部服务令牌签发。M03 完成后与 Gateway 进行真实登录、刷新、注销、撤销的联调验收。

前置条件（均已满足）：

- M01 `educloud-common` 已实现并验证：统一响应、分页、错误基类、requestId/traceId、安全上下文、EventEnvelope、Redis Worker 租约 ID。
- M02 `educloud-gateway` 已实现并验证：17 条静态路由已包含 `/api/v1/auth/**`、`/users/**`、`/roles/**`、`/permissions/**`、`/platform-config/**`、`/security/**` 到 User；Gateway 消费侧会话契约（Redis session + JWT claims）已冻结并有 14 个集成测试背书。
- 共享依赖 Compose：MySQL 8.0.36（`educloud_user` 库与 `user_app`/`user_migration` 账号已建）、Redis 7.2.5、RabbitMQ 3.13.7、Nacos 2.3.2 均健康。
- 数据设计、安全设计、API 契约、业务流程、可靠性、开发测试、部署运维等 2026-08-18 文档集为批准基线；本规格与 M02 文档构成 M03 的执行依据。

## 2. 已比较方案与决定

| 方案 | 内容 | 决定 |
|---|---|---|
| 自研认证与会话（本项目既定） | User 服务自持 RSA 私钥签 JWT、DB+Redis 双写会话、行锁原子轮换 | **采用**；与 Gateway 已冻结的消费侧契约（Redis hash、claims、RS256/kid）完全对齐，不引入外部 IdP |
| Spring Authorization Server | 引入 OAuth2 授权服务器框架 | 不采用；M02 已按"User 签发、Gateway 校验"自研契约冻结，迁移成本高且 Refresh 轮换语义（会话族）需定制 |
| Keycloak 等外部 IdP | 独立身份服务 | 不采用；需要额外部署与数据同步，违反"一个事实一个权威服务"边界 |
| 密码哈希 | BCrypt（strength 10，Spring Security） | **采用**；JDK17 原生可用、自适应、无需额外 native 依赖；Argon2 需 Bouncy Castle，留作后续评估 |
| 会话存储 | MySQL `refresh_session`（权威事实）+ Redis（Gateway 在线撤销读模型） | **采用**；与 M02 `RedisSessionVerifier` 的 key/字段/TTL 语义逐字节对齐 |
| 轮换并发 | 按 `session_token_hash` 行锁，父 `ACTIVE→ROTATED` 原子迁移并插入子行 | **采用**；并发宽限窗口内稳定返回 `REFRESH_ALREADY_ROTATED`，窗口外重用撤销整个 `family_id` |
| 签名密钥管理 | User 从密钥文件加载 RSA 私钥；部署脚本导出公共 JWKS 供 Gateway 配置；`kid` 支持平滑轮换 | **采用**；Gateway 的 `JwksLoader` 只接受 `jwks-json`/`jwks-location` 静态来源，不支持远程 JWKS URL，因此不引入热加载端点 |
| 数据访问 | MyBatis-Plus（ASSIGN_ID 雪花、乐观锁、参数绑定） | **采用**；与数据设计与开发规范一致；父 POM 增加版本管理 |
| 服务间认证 | HTTP Basic `client_credentials` 签发 5 分钟服务 Token | **采用**；凭据只存哈希，双凭据 `ACTIVE/GRACE/REVOKED` 轮换 |
| 前端认证适配 | M03 不修改前端 | **采用**；旧路线图 Task 20 的认证适配在 M03 完成真实认证后执行 |

## 3. 范围与边界

### 3.1 交付范围（M03 内）

- 学生自助注册（公开开关控制）、登录、刷新、注销、改密。
- 账号状态：锁定（失败阈值）、禁用/恢复；锁定到期自动放行。
- 用户档案：本人读写；头像以 `avatar_file_id` 记录（File 批量授权留 M04 后接线，M03 用明确替身断言"不落库、不 N+1"契约）。
- RBAC：内置角色与权限目录 seed、用户角色分配、角色维护、权限目录查询。
- 管理端用户：分页、详情（脱敏）、状态变更、角色分配。
- 平台公开配置：匿名读、权限写。
- JWT 签名公钥状态查询（不含私钥）。
- 内部服务令牌：`service_client`/`service_client_credential`、`POST /internal/v1/service-tokens`、凭据轮换脚本。
- 内部状态快照：`GET /internal/v1/users/{id}/status-snapshot`（登记消费者校准用）。
- Outbox 发布：`UserRegistered`、`UserStatusChanged`、`RoleAssignmentChanged`、`AuditEventPublished`。
- 与 Gateway 的真实登录联调门禁（Rocky 端到端）。

### 3.2 非目标（M03 明确不做）

- 找回/重置密码（`【后续规划】`，需一次性令牌、通知、页面闭环）。
- 用户删除/匿名化（首期只禁用/恢复；匿名化待专项合规方案批准）。
- 前端三端认证适配（M03 验收后按旧路线图 Task 20 重排执行）。
- File 服务（M04）：头像地址仅保留契约与替身，不实现远端 grant。
- 手机号/邮箱验证码验证（`email_verified` 字段保留，验证流程后续）。
- 多租户、社交登录、MFA。

### 3.3 领域聚合

- 账号（`sys_user`）：登录名/邮箱/手机唯一、密码哈希、状态、tokenVersion、失败计数。
- 档案（`user_profile`）：展示名、头像 fileId、简介、locale。
- RBAC（`sys_role`/`sys_permission`/`sys_user_role`/`sys_role_permission`）。
- 会话（`refresh_session`）：会话族、轮换、撤销、客户端指纹。
- 服务客户端（`service_client`/`service_client_credential`）。
- 平台公开配置（`platform_public_config`）。
- 登录审计（`login_audit`）与通用审计（`audit_event`）。

## 4. 认证与会话契约（与 Gateway 对齐，最高优先级）

### 4.1 Redis 会话读模型（M03 写入，Gateway 读取）

```text
key    = educloud:{environment:auth}:session:{sid}
hash   = { subject: <sub>, status: <ACTIVE|REVOKED>, tokenVersion: <long> }
TTL    = 必设且 > 0；保留期 >= 该会话已签发 Access Token 的最大剩余有效期（默认取 accessTokenTtl 配置）
```

- `environment` 与 Gateway 的 `educloud.gateway.environment` 必须同源：User 服务属性 `educloud.user.session.environment` 读取同一 `EDUCLOUD_ENVIRONMENT` 环境变量（默认 `local`）。两服务 environment 不一致时会话必然 401，部署/契约测试必须校验一致。
- `subject` = JWT `sub`（用户 ID 字符串）；`status` 只允许 `ACTIVE`/`REVOKED`（Gateway 对其它值返回 CORRUPT，映射 503）；`tokenVersion` 必须与 JWT claim 精确相等（Long 比较）。
- 写入时机：登录成功、刷新成功后写入/刷新 ACTIVE；注销、禁用、改密、会话族重用检测时置 `REVOKED`（保留到 Access Token 过期，不立即删除）。
- 共享 Redis 实例与数据库索引：本地 Compose 同一 Redis 7.2.5，不额外指定 DB 编号（默认 0）。

### 4.2 Access Token（User 签发，Gateway 校验）

- 算法：RS256；头部 `alg=RS256`、`typ=JWT`、`kid`（当前活动密钥）。
- 默认有效期 15 分钟（`educloud.user.jwt.access-token-ttl`）。
- 必需 claims（Gateway `GatewayJwtValidator` 逐一校验，缺一即 401）：
  - `iss`：与 Gateway `educloud.gateway.security.issuer` 精确一致（本地 `https://issuer.educloud.local`）。
  - `aud`：集合，必须包含 Gateway `educloud.gateway.security.audience`（默认 `educloud-api`）。
  - `sub`：用户 ID 字符串，匹配 `[A-Za-z0-9._:-]{1,128}`。
  - `sid`：会话族 ID（`refresh_session.family_id`），匹配同一字符集。
  - `tokenVersion`：非负整数（`sys_user.token_version` 当前值）。
  - `userType`：`STUDENT|TEACHER|ADMIN`（Gateway 硬编码集合；业务角色如 SUPER_ADMIN 属于 roles，不是 userType）。
  - `roles`：角色 code 集合（可空）。
  - `permissions`：权限码集合（可空）；**全量去重后不得超过 64 个**（Gateway 硬上限），且每个匹配 `[A-Za-z0-9:._*-]{1,128}`。
  - `iat/nbf/exp`：NumericDate；nbf=iat 前 5 秒容差内。
- 不携带手机号、邮箱、密码、密钥等非必要个人信息。

### 4.3 Refresh Token 与轮换

- Refresh Token 本体是随机 256-bit（Base64url），只以 SHA-256 哈希存 `refresh_session.session_token_hash`；返回给浏览器前写 HttpOnly Cookie，业务响应永不返回 Refresh Token 明文。
- `refresh_session` 字段语义：`family_id`=Access Token 的 `sid`；`token_id` 唯一；`parent_token_id`/`replaced_by_token_id` 链；`status` 属于 `ACTIVE/ROTATED/REVOKED/EXPIRED`；`client_fingerprint_hash`=归一化 UA 的 SHA-256。
- 轮换事务（同一本地事务）：
  1. `SELECT ... FOR UPDATE` 按 `session_token_hash` 锁父行。
  2. 父行 `ACTIVE` 且未过期：置 `ROTATED`（记录 `consumed_at`），插入子行（`ACTIVE`、新 `token_id`、`parent_token_id`=父 `token_id`）。
  3. Redis hash 刷新为 ACTIVE（subject/tokenVersion 取最新值）。
  4. 响应新 Access Token + 新 Refresh Cookie（同 `family_id`）。
- 并发/重用判定：
  - 锁等待后父行已 `ROTATED`：若在并发宽限窗口（默认 5s，`educloud.user.session.rotation-grace-window`）内：返回 409 `REFRESH_ALREADY_ROTATED`（稳定冲突，不撤销）；窗口外：撤销整个 `family_id`（全行 `REVOKED` + Redis `REVOKED`）+ 安全审计（`SESSION_REUSE_DETECTED`）。
  - `client_fingerprint_hash` 与存储不一致：视为跨端重用，撤销家族并审计。
- 刷新拒绝条件：父行 `REVOKED`/`EXPIRED`、用户 `DISABLED`、用户 `token_version` 与签发时不一致、Redis 不可用按"认证失败关闭"（401，不降级放行）。

### 4.4 撤销矩阵

| 动作 | DB refresh_session | Redis | sys_user.token_version | 效果 |
|---|---|---|---|---|
| 注销 | 当前 family 置 REVOKED | 当前 sid 置 REVOKED | 不变 | 该族 Access/Refresh 失效；幂等 |
| 改密 | 除当前 family 外全部 REVOKED；当前 family 保留 ACTIVE | 全部 sid 置 REVOKED，当前 family 的 sid 用新 tokenVersion 重写 ACTIVE | +1 | 旧 Access 全部失效；当前会话可刷新续期 |
| 禁用 | 全部 REVOKED | 全部 REVOKED | +1 | 登录与刷新均拒绝，Gateway 立即拒绝旧 Access |
| 恢复 | 无（新登录重新建） | 无 | 不变 | 账号可登录 |
| 重用检测 | 家族全 REVOKED | 家族 sid 置 REVOKED | 不变 | Gateway 拒绝族内 Access |
| 角色敏感变更 | 可选全量撤销（配置开关，默认仅记录） | 同左 | 可选 +1 | 权限立即收敛 |

- 撤销写 Redis 失败：以 DB 事务为准，Redis 用重试/补偿任务补齐；Gateway 在 Redis 不可用时对受保护请求失败关闭（M02 已实现），User 服务不因此放行。

### 4.5 Refresh Cookie 与 CORS

- Cookie 名 `refresh_token`；`HttpOnly`；`Path=/api/v1/auth`；`SameSite=Lax`；`Secure` 由 `educloud.user.session.cookie-secure` 配置（生产 true，本地 false）。
- 刷新/注销接口读取 Cookie；同源（经 Gateway 代理）无需 CORS 凭据；跨域携带凭据时依赖 Gateway 精确 origin 白名单（M02 已实现，禁止 `*`）。
- 前端单飞刷新由前端适配层保证（M03 验收后）；服务端并发宽限窗口已容忍偶发并发刷新。

## 5. 账号与登录保护

- 登录名解析：username 或 email 或 phone（统一失败语义 `INVALID_CREDENTIALS` 401，不区分账号不存在/密码错误）。
- 密码策略：最小长度 8、最大 128；BCrypt strength 10；不记录明文、不返回哈希。
- 失败锁定：连续失败 `failed_login_count` 大于等于阈值（默认 5）置 `LOCKED` + `locked_until`（默认 15 分钟）；到期后登录自动放行并重置计数。LOCKED 期间登录返回 423 `ACCOUNT_LOCKED`（附锁定剩余秒数）；DISABLED 返回 403 `ACCOUNT_DISABLED`。
- 成功登录：重置失败计数、更新 `last_login_at`、写 `login_audit`（登录名脱敏、结果、IP、UA、requestId；不记录密码）。
- 注册：仅 `STUDENT`；由配置开关控制（`educloud.user.registration.enabled`，默认 true）；用户名/邮箱/手机唯一由数据库唯一索引最终保护，并发冲突映射为 409 明确错误码；注册成功后自动分配 `STUDENT` 角色并发布 `UserRegistered`。
- 登录/刷新/注销/注册的滥用防护依赖 Gateway 入口限流（M02 已实现 login-ip/login-account 桶 + ordinary 桶），User 服务不重复实现，但契约测试必须验证限流语义仍生效。

## 6. RBAC 与权限

- 角色种子（V001 迁移 seed，`built_in=1`）：STUDENT、TEACHER、COURSE_REVIEWER、CONTENT_REVIEWER、FINANCE_ADMIN、SYSTEM_ADMIN、SUPER_ADMIN。
- 权限目录 seed：先落 User 域权限码（`user:read`、`user:status:update`、`rbac:read`、`rbac:manage`、`rbac:assign`、`platform:config:read`、`platform:config:update`、`security:key-status:read`、`audit:read`）与基础角色映射；Course/Content 等其它权限码由后续模块 seed（权限码只增不改，废弃需迁移+版本记录）。
- **JWT 载荷约束**：`permissions` 全量去重不超过 64（Gateway 上限）。角色映射权限总和超限时，服务启动校验失败（fail-fast），不允许签发截断载荷。
- 授权：Controller 方法级 `@PreAuthorize` + 权限码；资源归属（本人档案）在 Service 层校验；管理端查询返回脱敏数据（手机/邮箱部分遮蔽）。
- 角色分配/权限变更写审计并发布 `RoleAssignmentChanged`；角色敏感变更撤销会话由配置开关控制。

## 7. 数据库设计（引用数据设计第 3 节，落地要点）

- `V000__technical_tables.sql`：`schema_migration_history`、`outbox_event`、`outbox_sequence`、`inbox_event`（模板，M03 无订阅源，标注未启用）、`audit_event`、`idempotency_record`（注册幂等用，唯一 `(user_id, operation, idempotency_key_hash)`；匿名注册 user_id 用 0 约定）。
- `V001__user_identity_and_rbac.sql`：sys_user、user_profile、sys_role、sys_permission、sys_user_role、sys_role_permission + 角色/权限 seed + 索引。
- `V002__session_and_platform.sql`：refresh_session、service_client、service_client_credential、platform_public_config、login_audit + 索引 + 公开配置 seed。
- 字段、索引、唯一约束严格按数据设计；时间 `DATETIME(3)` UTC；主键 BIGINT（MyBatis-Plus ASSIGN_ID，User datacenterId=0，Worker 槽位租约复用 Common 的 `WorkerLeaseIdentifierGenerator`）。
- 迁移器：新建 `deploy/scripts/run-migrations.sh`（MySQL GET_LOCK、VNNN 顺序、SHA-256 checksum、`schema_migration_history`、失败停止）；契约测试覆盖空库升级、重复执行保护、checksum 篡改拒绝。

## 8. 内部服务令牌（服务间认证）

- `POST /internal/v1/service-tokens`：HTTP Basic（`client_id:client_secret`）+ 表单 `grant_type=client_credentials&audience=<target>&scope=<...>`；响应 `{access_token, token_type=Bearer, expires_in=300}`。
- 校验：credential `ACTIVE`（或 `GRACE` 期内）、secret SHA-256 匹配、client `ACTIVE`、audience 属于 `allowed_audiences_json`、scope 属于 `allowed_scopes_json`。
- 服务 Token claims：`sub=service:<client_id>`、`clientId`、`aud`、`scope`、`jti`、`iat/exp`（5 分钟）、`tokenVersion`（client 当前值）；同一非对称签名体系。
- 凭据轮换：先建新 `ACTIVE` 凭据，旧凭据置 `GRACE`（默认 24h），到期任务置 `REVOKED`；同一 client 最多一个 ACTIVE + 一个 GRACE；客户端行锁串行化。
- `SERVICE_BOOTSTRAP_JOB`：`deploy/scripts/bootstrap-service-clients.sh`（Secret 从 stdin/文件读取，不进参数/日志/argv；相同值幂等，不同值拒绝隐式覆盖需显式 rotate）。
- 接口 ACL：每个 `/internal/v1` 接口在实现中登记允许的 `clientId/aud/scope`；`status-snapshot` 只允许登记的状态投影消费者；请求与响应不得包含凭据；非本地环境拒绝明文交换。

## 9. 事件与 Outbox

- 发布事件（事件信封使用 Common `EventEnvelope`，字段与数据设计一致）：
  - `UserRegistered`（聚合 User，data：userId、username 脱敏、userType）
  - `UserStatusChanged`（data：userId、fromStatus、toStatus、reason）
  - `RoleAssignmentChanged`（data：userId、roleCodes、assignedBy）
  - `AuditEventPublished`（审计事实，Analytics 消费）
- 路由键：`aggregateType:aggregateId`（如 `User:1960000000000000001`）保证同聚合有序。
- 发布：业务更新与 `outbox_event` 同事务；发布器小批锁定（`publish_status`+`next_attempt_at`），投递 RabbitMQ 交换机 `educloud.events` 后标记已发布；确认不明确允许重投（消费者幂等）；达重试阈值标记失败并告警，不静默丢弃。
- `source_sequence` 由 `outbox_sequence` 行锁定递增（与数据设计一致）。
- Inbox：M03 暂无可消费的上游事件源（File 事件 M04 才出现），`inbox_event` 建表但无消费者，作为技术模板就绪；条件门禁 N/A 理由记录。
- RabbitMQ 连接不可用：业务事务正常提交，Outbox 待发布；发布器重试；不得回滚业务。

## 10. API 契约（外部经 Gateway，内部接口仅内网）

### 10.1 外部 API

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /auth/register | 匿名 | 仅 STUDENT；开关、唯一约束、密码策略 |
| POST | /auth/login | 匿名 | {loginName,password,portal}；响应 data 含 accessToken/expiresIn/用户摘要；Refresh 走 Cookie |
| POST | /auth/refresh | Refresh Cookie | 原子轮换；返回新 Access + 新 Cookie |
| POST | /auth/logout | Cookie 或 Bearer | 撤销当前 family；幂等；清 Cookie |
| POST | /auth/password/change | Bearer | 旧密码校验，新哈希，撤销矩阵 |
| GET | /me | Bearer | 当前用户 + 角色/权限摘要 |
| PATCH | /me/profile | Bearer | 本人档案 |
| GET | /users | user:read | 管理分页（脱敏） |
| GET | /users/{id} | user:read | 详情（脱敏；无权限感知返回 404） |
| PATCH | /users/{id}/status | user:status:update | 锁定/禁用/恢复；请求带 version 乐观锁 |
| PUT | /users/{id}/roles | rbac:assign | 分配角色 |
| GET | /roles | rbac:read | 角色列表 |
| POST/PUT | /roles、/roles/{id} | rbac:manage | 角色维护 |
| GET | /permissions | rbac:read | 权限目录 |
| GET | /platform-config/public | 匿名 | 站点公开配置 |
| PUT | /platform-config/public | platform:config:update | 更新非敏感配置 |
| GET | /security/signing-key-status | security:key-status:read | 活动 kid、公钥数量、更新时间、下次轮换时间 |

### 10.2 内部 API（/internal/v1，服务身份保护）

| 方法 | 路径 | 允许调用方 | 说明 |
|---|---|---|---|
| POST | /service-tokens | 已登记服务客户端 | client_credentials 签发 |
| GET | /users/{id}/status-snapshot | 登记投影消费者 | {userId,status,tokenVersion,aggregateVersion} |
| GET | /public-jwks | 运维/登记消费者 | 公共 JWKS（不含私钥），供部署校验 |

### 10.3 关键请求/响应

登录请求：{"loginName":"student@example.com","password":"...","portal":"STUDENT"}

登录响应 data：{"accessToken":"...","expiresIn":900,"user":{"id":"1960000000000000001","username":"student01","displayName":"学生01","userType":"STUDENT","roles":["STUDENT"],"permissions":["course:read"]}}

刷新响应 data 同登录；错误码示例：INVALID_CREDENTIALS(401)、ACCOUNT_LOCKED(423)、ACCOUNT_DISABLED(403)、REFRESH_ALREADY_ROTATED(409)、TOKEN_EXPIRED(401)、SESSION_REVOKED(401)、USERNAME_TAKEN(409)、EMAIL_TAKEN(409)、PHONE_TAKEN(409)、PASSWORD_WEAK(422)、VERSION_CONFLICT(409)。

## 11. 配置与环境

- 关键配置（环境变量）：
  - EDUCLOUD_ENVIRONMENT（与 Gateway 同源；默认 local）
  - SERVER_PORT（默认 8082）
  - MYSQL_HOST/PORT/EDUCLOUD_USER_DB_PASSWORD（账号 user_app）
  - REDIS_HOST/PORT/REDIS_PASSWORD
  - RABBITMQ_HOST/PORT/USER/PASS
  - NACOS_SERVER_ADDR + EDUCLOUD_USER_NACOS_USERNAME/PASSWORD（注册 educloud-user，最小权限）
  - USER_JWT_PRIVATE_KEY_LOCATION（RSA 私钥文件，0600；生产 Secret 挂载）
  - GATEWAY_JWKS_LOCATION（部署脚本导出的公共 JWKS 文件，供 Gateway 配置；本地由 generate-user-jwt-keys.sh 生成同一密钥对）
  - EDUCLOUD_USER_JWT_ISSUER/AUDIENCE（与 Gateway GATEWAY_JWT_ISSUER/AUDIENCE 一致）
  - EDUCLOUD_USER_REGISTRATION_ENABLED、会话/锁定/宽限窗口等。
- Nacos：仅服务发现（注册 educloud-user）；不依赖 Nacos 配置中心（配置全部走 env/文件，避免配置漂移）；provision 脚本为 educloud_user 建立最小权限（r/w naming educloud-user）。
- Compose：deploy/docker-compose/compose.yml 新增 educloud-user 服务（depends_on mysql/redis/rabbitmq/nacos；healthcheck；env 引用 .env）；同步更新 compose-contract-tests.sh（允许 user，仍拒绝 M04+ 业务服务）。
- .env.example 增加 User 服务变量与 JWT 密钥路径占位；真实值进忽略文件。

## 12. 可观测性与安全

- 健康检查：liveness（进程/上下文）、readiness（MySQL/Redis/RabbitMQ/Nacos 可达）；Management 端口 8083（127.0.0.1）。
- 指标：Micrometer + Prometheus（/actuator/prometheus）；业务指标（登录成功/失败、刷新轮换、重用检测、注册、撤销）低基数 tag。
- 日志：字段 service=educloud-user environment=... instance=... requestId traceId userId operation httpMethod path httpStatus durationMs errorCode；密码/Token/Cookie/密钥绝不入日志；登录名、手机、邮箱脱敏。
- 追踪：Brave + Zipkin（与 Gateway 一致，management.tracing.sampling.probability 配置）。
- 审计：登录失败、会话重用、锁定/禁用、角色权限变更、用户状态变更、平台配置变更必须写 audit_event（INSERT/SELECT-only 权限）并发布 AuditEventPublished。
- 安全：Controller 全量 Bean Validation；密码/哈希/Token 永不进入响应 DTO；管理查询脱敏；错误响应无堆栈/SQL/内部路径；统一失败语义防枚举；注册/登录入口依赖 Gateway 限流。

## 13. 测试策略

- 单元：认证 Service（登录/锁定/统一失败）、刷新轮换状态机（父子链/宽限/重用撤销）、撤销矩阵、密码策略、RBAC 权限汇总（64 上限校验）、服务令牌（签发/ACL/轮换）、Outbox 同事务。
- Web/安全切片：Controller 校验、HTTP 状态与错误码、Cookie 属性、方法权限、脱敏、伪造身份头。
- 数据集成（MySQL 8.0.36 Testcontainer，复用 Common 镜像解析器 + 私有镜像 env 覆盖）：唯一约束、乐观锁、行锁轮换并发、迁移（空库/升级/重复执行/checksum）。
- 消息：Outbox 同事务发布、重试、投递后标记、幂等重投。
- 契约：deploy/tests/user-module-contract-tests.sh（依赖边界、包结构、迁移存在、无密钥提交、无业务越界）。
- 端到端（Rocky）：deploy/tests/user-gateway-e2e-tests.sh——真实启动 MySQL/Redis/Nacos/RabbitMQ + User + Gateway：注册、登录、/me、刷新（含并发宽限）、注销后旧 Access 401；禁用后旧 Access 立即 401；改密后旧 Access 401、刷新续期成功；会话重用检测撤销家族；Gateway 侧 Redis 失败关闭语义不回归。
- 迁移测试：空库到最新、上一发布版本带数据升级、重复执行保护、回退说明。

## 14. 后续（M03 验收后）

- 三端前端认证适配（旧路线图 Task 20）：Axios 单飞刷新、401 处理、localStorage 移除。
- 找回/重置密码专项。
- 用户删除/匿名化专项合规方案。
- M04 File 接线：头像批量授权、状态快照消费者落地（inbox_event 启用）。
- 其余模块接入服务令牌（M04+ 依赖 POST /internal/v1/service-tokens）。

## 15. 完成定义（草案）

- [ ] 父 POM 新增 educloud-user（顺序 common 到 gateway 到 user）；JDK 17/21 双构建通过，字节码 61。
- [ ] 数据库迁移 V000/V001/V002 在 MySQL 8.0.36 空库升级、重复执行保护、checksum 校验通过；user_app 无库级写权限。
- [ ] 注册/登录/刷新/注销/改密/锁定/禁用/恢复全部有确定性失败测试与集成证据。
- [ ] Refresh 原子轮换、并发宽限、窗口外重用撤销家族、指纹不匹配撤销通过测试；Gateway 在注销/禁用后立即拒绝原 Access Token（e2e 证据）。
- [ ] Redis 会话 key/字段/TTL 与 Gateway RedisSessionVerifier 逐字节对齐（契约测试断言）。
- [ ] JWT claims（iss/aud/sub/sid/tokenVersion/userType/roles/permissions）满足 Gateway 校验器；权限 64 上限校验；JWKS 无私钥参数。
- [ ] RBAC 角色/权限 seed、分配、方法授权、脱敏与审计通过；平台公开配置不含 Secret。
- [ ] 服务令牌 client_credentials、哈希存储、ACTIVE/GRACE/REVOKED 轮换、ACL、凭据不进 URL/日志通过测试。
- [ ] Outbox 同事务发布 UserRegistered/UserStatusChanged/RoleAssignmentChanged/AuditEventPublished，重试与幂等通过。
- [ ] Rocky 端到端（User+Gateway 真实登录联调）通过；共享依赖保持健康；工作区干净。
- [ ] 无用户删除/匿名化、无找回密码端点；README 与文档如实标注 M03 边界。
- [ ] 独立代码审查无未解决"必须修复"项；用户书面验收后进入 M04。
