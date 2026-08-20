# EduCloud M02 Gateway 模块设计规格

> 日期：2026-08-20
>
> 状态：设计已批准，等待书面规格审阅
>
> 模块：M02 `educloud-gateway`
>
> 交付类型：可独立运行的 Spring Cloud Gateway WebFlux 应用

## 1. 目的与前置条件

M02 为学生端、教师端和管理端建立唯一后端入口，并交付可验证的安全入口基线。它负责路由、入口认证、会话撤销检查、跨域、入口限流、请求追踪和 Gateway 自有错误响应，不拥有业务数据，也不承担领域授权或跨服务业务编排。

M01 `educloud-common` 已实现并完成 JDK 17/21、Redis Testcontainers、模块契约和中文代码审查门禁。Rocky Linux 8.9 上的共享 Redis 7.2.5 与 Nacos 2.3.2 已健康运行。M02 继续使用当前 `codex/educloud-backend-foundation` 分支和隔离 worktree，不创建新的工作树，不使用子智能体。

M03 `educloud-user` 尚未实现，因此 M02 不提供登录响应、不签发 Token、不创建 Refresh 会话。M02 以运行时生成的测试密钥、隔离 Redis 会话数据和受控 Nacos 测试环境验证完整消费侧契约；M03 完成后再进行真实登录、刷新、注销和权限联调。

## 2. 已比较方案与决定

| 方案 | 内容 | 决定 |
|---|---|---|
| 安全优先 Reactive Gateway | WebFlux Gateway、配置式 JWKS、逐请求 Redis 会话检查、自定义 Redis 双维度限流、静态路由 | **采用**；完整满足安全入口基线且不依赖 M03 HTTP 服务 |
| 以内置能力为主 | 标准 JWT、内置 IP RedisRateLimiter、静态路由 | 不采用；不能直接覆盖账号维度登录限流和权威会话契约 |
| Nacos/Sentinel 动态策略 | 动态路由、动态限流和运行时策略 | 不采用；首期复杂度和配置漂移风险过高，安全策略可能绕过代码审查 |

已批准的关键决定如下：

1. M02 实现真实 JWT 验签和 Redis 撤销契约，不提供 Token 签发或认证关闭模式。
2. 缺失或非法 JWT 安全配置时启动失败，不以降级模式接受流量。
3. JWKS 由环境变量、挂载文件或 Nacos 配置提供，支持多个公钥和 `kid` 平滑轮换。
4. 每个受保护请求都读取 Redis 权威会话状态；M02 不增加本地正缓存。
5. 匿名访问使用“HTTP 方法 + 精确路径”的最小白名单，其他路径默认认证。
6. Gateway 删除伪造身份头，只向下游传递已验证的原始 Bearer Token、requestId 和 Trace Context。
7. 限流使用 Redis 分布式 IP/路由组和登录账号哈希双维度策略。
8. 路由表在 `application.yml` 中版本化；Nacos 只提供配置和服务发现，不自动暴露路由。
9. M02 只验证功能与安全语义，不声明生产 RPS、P95/P99 或可用性 SLO。
10. Gateway 使用专用最小权限 Nacos 客户端账号，不使用默认管理员账号运行。

## 3. 模块边界与依赖

### 3.1 运行模型

`educloud-gateway` 是 Spring Boot 可执行 JAR，使用 Reactive WebFlux，监听本地开发端口 8080，应用名固定为 `educloud-gateway`。父 POM 的模块顺序为：

```text
educloud-common
educloud-gateway
```

主要运行依赖为：

- `educloud-common`
- Spring Cloud Gateway WebFlux
- Spring Security OAuth2 Resource Server 与 JOSE
- Spring Data Redis Reactive
- Spring Cloud LoadBalancer
- Spring Cloud Alibaba Nacos Discovery 与 Config
- Spring Boot Actuator、Prometheus Registry 与 Micrometer Tracing
- Spring Boot Validation

禁止引入 Spring MVC、JDBC、MyBatis-Plus、MySQL Driver、RabbitMQ、领域 Entity/Mapper、数据库迁移或业务服务源码依赖。Gateway 不拥有逻辑数据库，数据库迁移门禁为 `N/A（Gateway 不持久化业务事实）`。

### 3.2 包职责

```text
com.educloud.gateway
├─ GatewayApplication
├─ config          强类型属性、启动校验和显式 Bean
├─ route           路由契约、保留路径和路由元数据
├─ security        JWKS、JWT、Redis 会话和身份头清理
├─ ratelimit       登录身份提取、HMAC key 和 Redis Lua 限流
├─ web             requestId、CORS、请求边界和安全响应头
├─ error           Reactive Gateway 自有错误映射
└─ observability   readiness、指标与低基数观测约束
```

Gateway 可以复用 Common 的 `ApiResponse`、`CommonErrorCode` 和 `RequestIdPolicy` 等无 Servlet 依赖的契约。Common 的 Servlet Filter、Servlet Security Context 和 MVC Advice 不会在 Reactive 应用中加载。Gateway 使用 `ServerWebExchange` 和 Reactive Security Context 实现自己的请求上下文与错误处理。

## 4. 请求处理顺序

每个请求按以下顺序处理；顺序通过集成测试锁定：

1. 拒绝 `/internal/v1/**` 外部请求并生成统一 404。
2. 使用 Common 的 requestId 规则保留合法 `X-Request-Id` 或生成 UUID；写入 exchange、响应头、日志上下文和下游请求。
3. 校验 CORS/预检请求，添加安全响应头并执行请求头、初始行和请求体上限。
4. 删除浏览器提供的保留身份头和不受信 `Forwarded/X-Forwarded-For`。
5. 解析可信客户端 IP，执行 IP/路由组限流；登录请求安全读取、回放请求体并执行账号维度限流。
6. 按 HTTP 方法和路径判断匿名接口、非用户 JWT 回调接口或受保护接口。
7. 请求携带 Bearer Token 时始终执行 JWT 验证；匿名接口上的无效 Token 也返回 401。
8. 受保护接口以及任何携带已验签 Bearer Token 的匿名接口执行 Redis 会话权威检查；匿名接口未携带 Token 时不创建伪身份。
9. 认证成功后保留原始 Bearer Token；不生成 `X-User-Id`、`X-Role` 或权限头。
10. 根据版本化静态路由和 Nacos 健康实例转发请求。
11. 透传下游正常响应和业务错误；仅由 Gateway 生成其自身的路由、连接、超时、安全和限流错误。

## 5. JWT 与 JWKS 契约

### 5.1 JWKS 来源和启动校验

配置属性支持两个互斥来源：

- `educloud.gateway.security.jwks-json`：环境变量或 Nacos Config 注入的公钥 JWKS JSON。
- `educloud.gateway.security.jwks-location`：只读挂载文件的位置。

必须且只能配置其中一个。JWKS 在启动时完整解析并原子构造不可变验证器，必须满足：

- 至少包含一个 RSA 签名公钥。
- 每个 key 的 `kid` 非空且唯一。
- `kty=RSA`、`use=sig`、`alg=RS256`。
- 不允许 `d`、`p`、`q` 或其他私钥参数。
- 不接受 HMAC/对称 key 或未知算法。

JWKS 是公开验证材料，不包含私钥。生产签名私钥只属于 M03 User 服务的 Secret。轮换时配置同时保留当前和上一把仍可能验证未过期 Access Token 的公钥；删除旧 key 前必须等待其签发 Token 的最长有效期结束。

Spring Security Reactive Resource Server 负责 Bearer Token 提取和基础 JWT 处理；M02 提供基于内存 JWKS 的 `ReactiveJwtDecoder` 与组合校验器，不访问远程 User JWKS 端点。官方 Reactive JWT 能力说明见 [Spring Security Reactive JWT](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html)。

### 5.2 JWT 验证规则

只接受 `RS256`。Header 必须包含已知 `kid`，JWT 必须满足：

- `iss` 与必填配置 `educloud.gateway.security.issuer` 精确相等。
- `aud` 包含配置值，默认逻辑受众为 `educloud-api`。
- `exp`、`nbf`、`iat` 类型正确且符合当前 UTC 时间。
- 默认时钟偏差为 30 秒，配置只允许 0～120 秒。
- `sub`、`sid`、`userType`、`tokenVersion`、`iat`、`exp`、`iss`、`aud` 全部存在。
- `sub` 和 `sid` 是 1～128 字符的安全文本；`tokenVersion` 是非负整数。
- `userType` 只能是 `STUDENT`、`TEACHER` 或 `ADMIN`。
- 可选角色与权限摘要必须是有界、非空文本集合；Gateway 不据此执行领域资源授权。

缺失 Token、签名错误、未知 `kid`、算法不符或 claims 不合法统一返回 401 `UNAUTHENTICATED`，不向客户端区分详细密码学失败原因。内部日志只记录失败类别、requestId 和 routeId，不记录 Token 或 claims 全文。

## 6. Redis 会话权威检查

Redis key 固定为：

```text
educloud:{<environment>:auth}:session:<sid>
```

Hash 字段固定为：

```text
subject=<JWT sub>
status=ACTIVE|REVOKED
tokenVersion=<非负整数>
```

M03 创建或刷新会话时必须先写入完整 Hash 和正 TTL，再把对应 Access Token 返回客户端。TTL 不得短于该会话族仍可能存在的最长 Access Token 剩余时间。

Gateway 使用单个只读 Lua 脚本原子执行 `HMGET` 和 `PTTL`，结果分类如下：

| Redis 结果 | Gateway 行为 |
|---|---|
| key 不存在 | 401 `UNAUTHENTICATED` |
| `status=REVOKED` | 401 `UNAUTHENTICATED` |
| subject 或 tokenVersion 不匹配 | 401 `UNAUTHENTICATED` |
| ACTIVE、字段匹配且 TTL > 0 | 继续请求 |
| 字段损坏、类型错误或 TTL <= 0 | 503 `DEPENDENCY_UNAVAILABLE` 并记录配置损坏指标 |
| Redis 命令失败、超时或返回协议异常 | 503 `DEPENDENCY_UNAVAILABLE` |

M02 每个受保护请求和每个携带 Bearer Token 的匿名请求都执行权威读取，不设置本地正缓存。这样，已撤销 Token 不能在公开接口上以“可选认证”身份继续传给下游。Redis 故障时受保护接口、携带 Token 的匿名接口、登录、注册、刷新和支付回调全部失败关闭；未携带 Token 的公开只读目录可以跳过限流继续访问，但必须增加降级指标并采样告警。

## 7. 身份头与客户端 IP

Gateway 在任何认证处理前删除以下浏览器可伪造身份头及其大小写变体：

```text
X-User-Id
X-User-Type
X-Role
X-Roles
X-Permission
X-Permissions
X-Authenticated-User
X-EduCloud-Identity-*
```

认证成功后不重新注入普通身份头。Gateway 只传递已验证的原始 `Authorization: Bearer ...`、`X-Request-Id` 和 W3C Trace Context。各业务服务仍须独立验签、检查受众、执行方法权限和资源归属校验。

客户端 IP 默认使用 TCP 对端地址。只有直接对端属于显式配置的受信代理 CIDR 时，才按固定跳数解析标准 `Forwarded` 或 `X-Forwarded-For`；其他请求删除转发头。CIDR、跳数和地址解析必须有边界测试，不能默认信任任意代理头。

## 8. 路由与访问策略

### 8.1 路由表

路由使用 `application.yml` 中的显式 `spring.cloud.gateway.routes`，Nacos Discovery 只解析 `lb://` 服务实例。禁止启用 discovery locator 自动路由。Spring Cloud Gateway 支持属性化路由和 `lb:ws://serviceId` WebSocket 路由，见 [Gateway 配置](https://docs.spring.io/spring-cloud-gateway/reference/4.1/spring-cloud-gateway/configuration.html) 与 [WebSocket 路由](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/index.html)。

固定优先级如下：

| order | 外部路径 | 目标服务 |
|---:|---|---|
| 10 | `/api/v1/auth/**`、`/api/v1/users/**`、`/api/v1/roles/**`、`/api/v1/permissions/**`、`/api/v1/platform-config/**`、`/api/v1/security/**` | `lb://educloud-user` |
| 20 | 精确 `/api/v1/me`、`/api/v1/me/profile` | `lb://educloud-user` |
| 30 | `/api/v1/me/assignments`、`/api/v1/me/exams`、`/api/v1/me/course-progress`、`/api/v1/me/courses/*/progress` | `lb://educloud-content` |
| 40 | `/api/v1/me/enrollments` | `lb://educloud-course` |
| 50 | `/api/v1/courses/*/chapters/**`、`/api/v1/courses/*/assignments/**`、`/api/v1/courses/*/exams/**` | `lb://educloud-content` |
| 60 | `/api/v1/chapters/**`、`/api/v1/coursewares/**`、`/api/v1/content-revisions/**`、`/api/v1/assignments/**`、`/api/v1/submissions/**`、`/api/v1/exams/**`、`/api/v1/exam-attempts/**`、`/api/v1/community/**`、`/api/v1/content-audits/**` | `lb://educloud-content` |
| 65 | `/api/v1/teacher/courses/*/content-draft`、`/api/v1/courses/*/content-drafts` | `lb://educloud-content` |
| 70 | `/api/v1/categories/**`、`/api/v1/course-drafts/**`、`/api/v1/course-audits/**`、`/api/v1/courses/**`、`/api/v1/teacher/courses/*/draft` | `lb://educloud-course` |
| 80 | `/api/v1/cart/**`、`/api/v1/orders/**`、`/api/v1/refund-requests/**` | `lb://educloud-order` |
| 90 | `/api/v1/payments/**`、`/api/v1/payment-callbacks/**`、`/api/v1/payment-refunds/**`、`/api/v1/reconciliations/**` | `lb://educloud-payment` |
| 100 | `/api/v1/live-rooms/**` | `lb://educloud-live` |
| 101 | `/ws/v1/live/**` | `lb:ws://educloud-live` |
| 110 | `/api/v1/files/**`、`/api/v1/file-upload-sessions/**` | `lb://educloud-file` |
| 120 | `/api/v1/notifications/**`、`/api/v1/notification-channels/**` | `lb://educloud-notification` |
| 130 | `/api/v1/analytics/**`、`/api/v1/audit-events/**` | `lb://educloud-analytics` |
| 140 | `/api/v1/search/**` | `lb://educloud-search` |
| 150 | `/api/v1/recommendations/**`、`/api/v1/assistant/**` | `lb://educloud-recommendation` |

不存在的外部路径返回 Gateway 404。已知路由没有健康实例返回 503。Gateway 不把失败请求转发到其他服务，不聚合跨服务写操作。

### 8.2 精确匿名白名单

只允许以下方法和路径不携带用户 Access Token：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET|HEAD /api/v1/platform-config/public`
- `GET|HEAD /api/v1/categories`
- `GET|HEAD /api/v1/courses`
- `GET|HEAD /api/v1/courses/{id}`，其中 `{id}` 只能占一个路径段
- `GET|HEAD /api/v1/search/courses`
- `GET|HEAD /api/v1/recommendations/courses`
- 通过 Origin 校验的 CORS `OPTIONS`
- 内部网络访问的 `/actuator/health/liveness` 与 `/actuator/health/readiness`

`POST /api/v1/payment-callbacks/**` 属于不使用用户 JWT 的外部渠道接口，但仍执行请求大小、IP 限流和下游渠道签名验证；Gateway 不把它表述为匿名业务操作。

其余 `/api/v1/**` 和全部 `/ws/v1/**` 默认要求 JWT 与 Redis 会话验证。`/internal/v1/**` 在认证前直接返回统一 404，永不进入 Nacos 查询或下游路由。

## 9. CORS、安全响应头和请求边界

### 9.1 CORS

本地允许来源固定为：

```text
http://localhost:5173
http://localhost:5174
http://localhost:5175
http://127.0.0.1:5173
http://127.0.0.1:5174
http://127.0.0.1:5175
```

生产环境必须显式配置一个或多个精确 HTTPS Origin；禁止 `*`、origin pattern 和携带凭据的通配来源。允许方法为 `GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS`，允许请求头固定为：

```text
Authorization
Content-Type
X-Request-Id
Idempotency-Key
If-Match
Accept-Language
```

允许响应头为 `X-Request-Id` 和 `Retry-After`。Refresh Cookie 使用 `allowCredentials=true`；实际 Cookie 属性和 Origin 二次校验由 M03 User 服务负责。

### 9.2 安全响应头

所有 Gateway 自有及下游响应增加：

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- 限制摄像头、麦克风、定位等能力的 `Permissions-Policy`
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`
- 非本地 HTTPS 环境的 HSTS

Gateway 删除暴露框架或服务器版本的响应头。不会覆盖业务服务更严格的安全响应头。

### 9.3 请求边界

- API 全局请求体上限：1 MiB。
- 登录、注册和刷新请求体上限：16 KiB。
- 支付回调请求体上限：256 KiB。
- 请求头总大小上限：16 KiB。
- HTTP 初始请求行上限：8 KiB。
- 文件二进制通过 MinIO 短期授权地址传输，不经过 Gateway。
- 登录限流只接受 JSON，安全提取 `loginName` 后必须把原始字节完整回放给下游；不能记录密码或请求体。

## 10. Redis 分布式限流

M02 使用基于 Redis `TIME` 的 Lua Token Bucket，不使用单机内存作为权威限流器。每个 bucket 独立原子更新并设置有界 TTL。默认功能基线为：

| 维度 | 默认值 |
|---|---:|
| 普通接口，IP + routeGroup | 20 次/秒，突发 40 |
| 登录 IP | 10 次/分钟 |
| 登录账号 | 5 次/5 分钟 |
| 支付回调 IP | 60 次/分钟 |

阈值可通过强类型配置调整，但必须为正数并设置上限；生产不提供完全关闭限流的配置。默认值只用于功能和安全基线，不构成容量承诺。

Redis key 不保存原始 IP 或账号。Gateway 使用必填的 32 字节以上 HMAC-SHA256 Secret 对规范化维度值生成固定摘要。Secret 只来自环境变量或 Kubernetes Secret，不进入 Nacos、日志、指标、Trace 或 Git。

达到限额返回 HTTP 429、`RATE_LIMITED` 和 `Retry-After`。Redis 故障时登录、注册、刷新、支付回调及受保护接口返回 503；公开只读目录继续工作并增加 `gateway.ratelimit.degraded` 指标。降级告警采样，避免每请求重复 ERROR。

## 11. Nacos 客户端身份和配置

现有 Nacos 服务端 identity key/value 只用于服务端身份，不能替代应用客户端账号。Nacos 官方说明鉴权配置及客户端用户边界，见 [Nacos 2.3 鉴权](https://nacos.io/en/docs/v2.3/guide/user/auth/) 与 [Spring Cloud Alibaba Nacos 接入](https://sca.aliyun.com/en/docs/2023/user-guide/nacos/quick-start/)。

M02 增加幂等的本地 Nacos 账号配置与预检流程：

- 创建专用用户 `educloud_gateway` 和专用角色。
- 只授予 Gateway 所在 namespace/group 的配置读取、服务注册和服务查询权限。
- Gateway 用户名和随机密码写入未提交的 `deploy/docker-compose/.env`；示例文件只保留占位值。
- 管理员凭据通过环境或标准输入提供，不出现在命令参数、stdout、日志或 Git。
- 脚本重复执行时验证并保留相同目标状态；权限或密码不匹配时失败，不静默扩大权限。
- Gateway 缺少 Nacos 客户端用户名或密码时启动失败。
- 启动后 Nacos 短暂不可用不会杀死进程，但 readiness 变为不可用，现有请求按无健康实例语义失败。

配置中心使用 Spring Cloud Alibaba 2023 的 `spring.config.import` 方式；路由表不从 Nacos 动态加载。Nacos 可以提供普通运行参数和公钥 JWKS，但 HMAC Secret、Redis 密码和其他 Secret 只能来自环境变量或挂载 Secret。

## 12. 超时、错误和故障语义

默认下游连接超时为 2 秒，普通 HTTP 响应超时为 15 秒。WebSocket 使用独立握手和空闲配置，不套用普通响应超时。M02 不自动重试下游请求；避免对非幂等写操作制造重复副作用。实际超时值可配置，生产容量阶段再依据压测和观测数据校准。

Gateway 自有错误码如下：

| HTTP | 错误码 | 场景 |
|---:|---|---|
| 400 | `GATEWAY_BAD_REQUEST` | Gateway 无法安全解析入口请求 |
| 401 | `UNAUTHENTICATED` | Token 或 Redis 会话认证失败 |
| 403 | `ACCESS_DENIED` | Origin 或入口策略拒绝 |
| 404 | `GATEWAY_ROUTE_NOT_FOUND` | 保留内部路径或无外部路由 |
| 413 | `GATEWAY_REQUEST_TOO_LARGE` | 请求体超过路由上限 |
| 415 | `GATEWAY_UNSUPPORTED_MEDIA_TYPE` | 登录等受限端点媒体类型错误 |
| 429 | `RATE_LIMITED` | Redis 限流拒绝 |
| 503 | `DEPENDENCY_UNAVAILABLE` | Redis、Nacos 或健康实例不可用 |
| 504 | `GATEWAY_TIMEOUT` | 下游响应超时 |
| 500 | `INTERNAL_ERROR` | 未预期 Gateway 异常 |

Gateway 错误统一序列化为 `ApiResponse<Void>`，`data=null`，requestId 与响应头一致。客户端响应不包含内部主机名、服务地址、Nacos namespace、Redis key、异常类名、堆栈或底层异常消息。未预期异常只在 Gateway 记录一次完整堆栈。

下游已经返回的成功或业务错误响应保持状态、正文和业务码，不由 Gateway 二次包装。Gateway 可以添加 requestId 和安全响应头，但不能把下游 4xx 改写为 5xx。

## 13. 健康、指标、日志和追踪

### 13.1 健康检查

- liveness 只判断 Gateway 事件循环和进程能否继续工作，不因 Redis/Nacos 短暂故障触发无限重启。
- readiness 检查有效 JWKS 已加载、Redis 可用和 Nacos Discovery 可用。
- Actuator 只暴露 health 与 Prometheus；敏感管理端点没有 Gateway 外部路由。
- 优雅停机先停止接收新请求，再在预算内等待进行中的 HTTP 请求结束。

### 13.2 指标与日志

指标只使用低基数标签：routeId、HTTP 方法、状态、结果和错误类别。禁止把 Token、sid、userId、账号、原始 IP 或动态完整路径作为标签。

日志字段包含 service、environment、instance、requestId、traceId、routeId、HTTP 状态、耗时和安全失败类别。密码、Token、Cookie、JWKS 全文、HMAC Secret、Nacos/Redis 凭据和完整请求体永不记录。相同依赖故障采用聚合指标和采样日志，避免错误风暴。

Gateway 接受并传播 W3C Trace Context。合法 requestId 使用 `[A-Za-z0-9._-]{1,64}`，非法或缺失值替换为 UUID。Trace 标签不保存敏感声明或请求正文。

## 14. 测试策略

### 14.1 单元与配置测试

覆盖：

- JWKS 缺失、非法 JSON、私钥、重复/空 `kid`、非 RSA/RS256 和无公钥。
- issuer、audience、时间声明、时钟偏差、必填 claims 和边界长度。
- Redis 会话结果分类、返回协议损坏和错误映射。
- 匿名白名单、内部路径、保留身份头和可信代理解析。
- 登录 JSON 提取、HMAC 摘要、请求体回放和敏感信息不进入 `toString`/日志。
- 限流参数、Origin、CIDR、超时、大小与 Nacos 凭据启动校验。

测试运行时使用 Java `KeyPairGenerator` 生成临时 RSA key；仓库不提交测试私钥。

### 14.2 Reactive Web 与路由测试

使用 `WebTestClient` 覆盖：

- 匿名、缺 Token、过期/错误 Token、错误 audience/issuer、未知 `kid`。
- 匿名接口携带非法、已撤销或会话不匹配的 Bearer Token 时返回 401；未携带 Token 时保持匿名。
- Redis 会话缺失、撤销、版本/subject 不匹配和依赖错误。
- 身份头清除、原始 Token 转发、requestId 和 Trace Context。
- CORS 允许/拒绝、OPTIONS、安全响应头、大小限制和统一错误 JSON。
- 11 个服务、所有重叠路径、WebSocket URI 和 route order。
- discovery locator 保持关闭，`/internal/v1/**` 无法路由。
- 下游正常业务响应保持原样，Gateway 连接失败和超时映射正确。

### 14.3 Docker 集成测试

Maven 默认 `verify` 不启动容器；`integration` profile 通过 Failsafe 运行 `*IT`：

- Redis 7.2.5：真实执行会话 Lua、TTL、撤销、版本不匹配、并发 Token Bucket、过期和 Redis 中断失败关闭。
- Nacos 2.3.2：启用鉴权，创建隔离 namespace、临时最小权限用户和临时下游 HTTP 实例，验证 Gateway 注册、服务发现、HTTP/WS 路由和无健康实例错误。
- 端到端 Security IT：临时 JWKS、真实 Redis 会话、合法/非法 Token、双维度限流和下游头部观察。

所有测试资源使用 UUID namespace/key 前缀并在 `finally` 清理。清理失败必须使测试失败并输出非敏感资源标识，不遗留 Nacos 实例、用户、namespace 或 Redis key。

### 14.4 模块契约测试

契约脚本必须验证：

- 父 POM 只新增 `educloud-gateway`，模块为可执行 JAR。
- Gateway 没有 MVC/JDBC/MyBatis/MySQL/RabbitMQ 和业务服务源码依赖。
- 没有 Entity、Mapper、数据库 migration 或私钥材料。
- 路由表显式且 discovery locator 关闭。
- `/internal/v1/**` 不存在外部路由。
- 正式依赖版本来自父 BOM，子模块不覆盖核心框架版本。

## 15. Rocky 运行验收

M02 是可运行模块，必须在 Rocky Linux 8.9 / JDK 21 / Maven 3.9+ / Docker 环境完成以下门禁：

1. 执行默认单元、模块契约、全量 Maven 和 `-Pintegration`；确认 Failsafe 明确运行 Redis/Nacos/Security IT。
2. 通过幂等脚本配置专用 Nacos Gateway 用户并验证最小权限；不使用默认管理员账号启动应用。
3. 在权限为 0700 的临时目录生成 RSA 测试 key/JWKS 和 HMAC Secret，只把公钥 JWKS交给 Gateway。
4. 使用共享 Redis/Nacos 启动 Boot JAR，验证 8080、liveness、readiness 和 Nacos 中的健康 Gateway 实例。
5. 验证受保护接口无 Token 返回 401、`/internal/v1/**` 返回 404、已知路由无实例返回结构化 503。
6. 验证允许/拒绝 Origin、requestId、安全响应头和限流响应。
7. 不停止共享 Redis/Nacos；依赖中断只在 Testcontainers 环境验证。
8. 停止 Gateway，删除临时 key/JWKS/HMAC 材料并确认没有残留进程。

Rocky 阶段没有 M03 User 实例，因此“真实登录、刷新、注销和业务权限联调”明确标记为 `M03 门禁`，不能在 M02 宣称完成。公开路径若没有下游实例返回 503 是当前模块顺序下的真实状态，不使用静态响应伪造业务成功。

## 16. 实施顺序与质量门禁

实现必须遵循：

1. 先写模块依赖和边界失败测试，再创建 Gateway 模块。
2. 按配置/JWKS、路由、Reactive Security、Redis 会话、限流、Web 防护、错误、可观测性顺序执行 TDD。
3. 每一批先观察目标失败，再写最小实现并运行局部绿灯。
4. 运行模块单元、Redis/Nacos 集成、父工程、JDK 17/21 和 Rocky 启动门禁。
5. 对照本规格进行范围审查，并使用中文代码审查检查架构、正确性、安全、性能、可维护性和风格。
6. 修复所有必须修复项后重新运行相关门禁。
7. 更新 README、设计状态和计划证据，向用户汇报并等待确认。
8. 未经用户确认，不进入 M03，也不修改主检出目录中的前端代码。

## 17. 完成定义

M02 只有同时满足以下条件才算完成：

1. `educloud-gateway` 是唯一新增模块，能在 Java 17 和 21 构建并生成可执行 JAR。
2. 静态路由覆盖 11 个未来服务、重叠路径和 WebSocket，自动发现路由关闭。
3. JWT/JWKS、Redis 会话、匿名白名单和身份头边界有确定性测试。
4. Redis 双维度限流、可信代理、CORS、安全响应头和请求大小有失败路径测试。
5. Gateway 自有错误符合统一响应，不泄露 Token、内部 URL、凭据或异常细节。
6. Redis 7.2.5 与 Nacos 2.3.2 集成测试真实执行且没有残留资源。
7. Rocky 上 Gateway 启动、健康、注册和入口错误语义通过。
8. 正式依赖没有数据库、MVC、RabbitMQ 或业务领域越界；数据库迁移标记为 N/A 并说明原因。
9. 中文代码审查没有未解决的必须修复项，工作区干净。
10. README 仍明确三套前端认证为 Mock，真实认证联调属于 M03；用户确认后才允许进入 M03。
