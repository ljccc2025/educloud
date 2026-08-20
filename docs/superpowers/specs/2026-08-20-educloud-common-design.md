# EduCloud M01 Common 模块设计规格

> 日期：2026-08-20
>
> 状态：已批准设计，待实施计划审阅
>
> 模块：M01 `educloud-common`
>
> 交付类型：不可独立运行的 Maven JAR

## 1. 目的与前置条件

M01 为后续 Gateway 和业务服务提供最小、稳定、无领域归属的公共工程能力。它不是微服务，不提供业务 API，也不拥有数据库。

准备阶段已经完成：Rocky Linux 8.9 前置检查通过，Java 21 下父 Maven 构建通过，Docker Compose `config` 返回成功，九个共享依赖容器以 `up -d --wait` 返回退出码 0；HTTP、Redis、RabbitMQ 及 MySQL 数据库和账号验证均通过。共享依赖保持运行以供后续模块使用。

本规格服从以下上位约束：

- [模块执行顺序与门禁](./2026-08-20-educloud-backend-module-execution.md)
- [开发、编码与测试规范](./2026-08-18-educloud-development-and-testing.md)
- [API、事件与前后端联调规范](./2026-08-18-educloud-api-and-integration.md)
- [服务边界与领域模块](./2026-08-18-educloud-services-and-domains.md)

## 2. 方案选择

设计阶段比较了三种边界：

| 方案 | 内容 | 结论 |
|---|---|---|
| 精简型 | 统一响应、分页、错误和请求上下文 | 能支撑基本 HTTP，但会把已知的安全、ID 和消息契约推迟到后续模块反复扩充 Common |
| 平衡型 | 精简型能力，加安全上下文接口、Redis Worker ID 租约、事件信封、幂等值对象和通用配置 | **采用**；覆盖 M02/M03 的确定性前置能力，同时不提前创建业务持久化 |
| 完整旧路线型 | 再加入 Outbox、Inbox、审计持久化和未来服务迁移 | 拒绝；会让 M01 侵入尚未开始的业务数据库，违反逐模块交付边界 |

## 3. 模块结构与边界

父工程在 M01 首次且只新增一个聚合模块：

```text
educloud-backend/
├─ pom.xml
└─ educloud-common/
   ├─ pom.xml
   └─ src/
      ├─ main/java/com/educloud/common/
      │  ├─ api/
      │  ├─ error/
      │  ├─ web/
      │  ├─ security/
      │  ├─ id/
      │  ├─ messaging/
      │  ├─ idempotency/
      │  └─ config/
      └─ test/java/com/educloud/common/
```

M01 允许包含：

- 统一响应、分页和错误契约。
- `requestId` 处理和可选真实 `traceId` 读取。
- 只读安全上下文接口及 Servlet 条件实现。
- Redis Worker 槽位租约和失败关闭的分布式 ID 生成。
- 不含发布或持久化行为的事件信封和幂等值对象。
- Jackson、UTC 时间、Bean Validation 和 Spring Boot 自动配置。

M01 禁止包含：

- `main()`、Spring Boot `Application` 启动类、端口、Nacos 注册和 Actuator 服务端点。
- Controller、业务 Service、Entity、Mapper、Repository 和业务客户端。
- User、Course、Content、Order、Payment 等领域类型。
- MySQL、MyBatis-Plus、RabbitMQ 或 Nacos 运行依赖。
- Outbox、Inbox、审计或幂等数据库实现，以及任何业务服务迁移。
- JWT 验签、路由、CORS、登录、会话和权限数据管理。

## 4. API 与分页契约

### 4.1 `ApiResponse<T>`

不可变响应固定包含：

```text
code: String
message: String
data: T
requestId: String
timestamp: Instant
```

约束如下：

- 成功默认使用稳定业务码 `SUCCESS`。
- `timestamp` 使用可注入 `Clock` 产生的 UTC `Instant`，JSON 为 ISO 8601。
- 响应工厂从当前请求上下文读取 `requestId`；不使用可变静态全局变量。
- `data` 可以为空，但其 JSON 行为必须由契约测试固定。
- Common 不全局把全部 `long` 转为字符串。业务 ID 在对外 DTO 中明确使用字符串，分页计数继续使用 JSON 数字。

### 4.2 `PageResponse<T>`

分页结构固定包含：

```text
items: List<T>
page: int
pageSize: int
total: long
totalPages: long
```

- `page` 从 1 开始，`pageSize` 必须为正数，`total` 不得为负数。
- `items` 防御性复制为不可变列表；空结果返回空列表而非 `null`。
- `totalPages` 由 `total` 和 `pageSize` 安全计算，覆盖零数据、整除和有余数场景。
- Common 不决定业务接口的最大页长和排序字段；各服务使用配置和白名单约束。

## 5. 错误契约

`ErrorCode` 是业务模块可实现的稳定接口，至少提供：

```text
code: String
httpStatus: int
defaultMessage: String
```

Common 提供基础错误码：

```text
VALIDATION_FAILED
UNAUTHENTICATED
ACCESS_DENIED
VERSION_CONFLICT
RATE_LIMITED
DEPENDENCY_UNAVAILABLE
INTERNAL_ERROR
```

`BusinessException` 携带 `ErrorCode` 和可选的类型化安全详情。任意异常消息、堆栈和内部对象不得直接作为客户端 `data`。

Servlet 全局异常处理遵守：

- Bean Validation 和错误 JSON 映射为 HTTP 400、`VALIDATION_FAILED`。
- `BusinessException` 使用其错误码对应的 HTTP 状态，业务码不替代 HTTP 语义。
- 未预期异常映射为 HTTP 500、`INTERNAL_ERROR`，只在服务端记录一次完整堆栈。
- 客户端响应不包含 SQL、主机名、内部 URL、异常类名、堆栈或 Secret。
- 错误响应头和响应体使用相同 `requestId`。

## 6. 请求和安全上下文

### 6.1 请求 ID

- 请求头名固定为 `X-Request-Id`。
- 接受 1～64 位 ASCII 字母、数字、点、下划线和连字符。
- 合法客户端值保留；缺失、空、超长或非法值替换为 UUID。
- 最终值写入请求属性、MDC、响应头和统一响应。
- 过滤器必须在 `finally` 中恢复或清理 MDC，正常和异常路径都不能污染复用线程。

### 6.2 Trace

- 已启用 Micrometer Tracing 时读取其真实 `traceId`。
- 未启用时允许 `traceId` 为空，不把 `requestId` 伪装成分布式 Trace。

### 6.3 安全上下文

`AuthenticatedUser` 是只读值对象：

```text
userId: String
sessionId: String
roles: Set<String>
permissions: Set<String>
```

- 集合防御性复制且不可修改。
- 匿名上下文返回 `Optional.empty()`，不使用 `0` 或伪造匿名主体。
- `SecurityContextFacade` 只读取已经认证的主体。
- Common 不信任浏览器传入的 `X-User-Id`、`X-Role` 等身份头。
- JWT 验签和 Reactive Security Context 由 M02/M03 提供。

## 7. Redis Worker ID 租约

公共抽象包括 `IdentifierGenerator`、`WorkerLeaseRepository`、Redis 实现和租约感知生成器。

### 7.1 租约

- 整个环境共享 0～31 共 32 个 Worker 槽位，Redis key 按环境隔离而不按服务隔离。
- 获取使用带 TTL 的原子 `SET NX`；租约所有者使用实例 UUID。
- 默认 TTL 为 30 秒，每 10 秒续租。
- 续租和释放使用 Lua 校验所有者，错误实例不能修改租约。
- 生成器在每次发号前检查本地已确认的租约截止时间；线程长暂停、网络分区或续租超时后不得继续使用过期槽位。
- 正常释放通过 Lua 同时记录该槽位最后发号毫秒水位；新持有者只有在 Redis 时间严格超过水位后才能从序列零开始，避免同一毫秒快速重启产生重复 ID。
- 异常退出不主动删除租约；新实例等待 TTL 到期，并按 Redis 时间确认旧租约窗口已经结束后才能复用槽位。
- 第 33 个并发实例明确启动失败；不能随机选择仍有效的槽位。
- Redis 不可达、续租失败或所有权变化时，生成器失败关闭并拒绝新 ID。

### 7.2 ID 布局

ID 使用正 63 位 `long`：

```text
41 位毫秒时间戳 + 5 位 Worker + 17 位毫秒内序列
```

- 项目 Epoch 固定为 `2026-01-01T00:00:00Z`。
- 同一毫秒序列耗尽时等待下一毫秒，不回绕。
- 时钟回拨不超过 5 毫秒时等待恢复；超过阈值时失败关闭。
- `Clock` 和等待器可替换，使测试不依赖真实时间。
- 分布式 ID 与事件的 `sourceSequence`、`aggregateVersion` 含义不同，禁止混用。

## 8. 事件与幂等契约

### 8.1 `EventEnvelope<T>`

固定字段为：

```text
eventId
eventType
eventVersion
sourceService
sourceSequence
aggregateType
aggregateId
aggregateVersion
occurredAt
requestId
traceId
data
```

创建时校验必填字段、正版本和有效 UTC 时间。`eventVersion` 是结构版本，`aggregateVersion` 是聚合业务顺序，`sourceSequence` 是来源提交水位，三者不可替代。

M01 不保存、发布或消费事件。业务模块未来在自己的本地事务中把信封写入 Outbox。

### 8.2 `IdempotencyKey`

值对象绑定：

```text
actorId
operation
key
requestDigest
```

- 同一主体、操作、键和摘要表示同一请求。
- 同一主体、操作和键但摘要不同表示冲突。
- M01 只负责格式和值语义，不提供数据库记录或 Repository。

## 9. 自动配置与依赖隔离

通过 Spring Boot 3 `AutoConfiguration.imports` 注册：

```text
CommonCoreAutoConfiguration
CommonServletWebAutoConfiguration
CommonSecurityAutoConfiguration
CommonIdentifierAutoConfiguration
```

- Core 提供 UTC `Clock`、响应工厂和 Jackson 时间配置。
- Servlet Web 仅在 Servlet 应用中提供过滤器和异常处理。
- Security 仅在存在 Spring Security 且为 Servlet 应用时提供默认读取实现。
- Identifier 默认关闭；只有显式启用且存在 Redis 连接时才竞争租约。
- 默认 Bean 使用 `@ConditionalOnMissingBean`，允许服务显式替换。
- Reactive Gateway 引用 Common 时不得激活 Servlet 配置。

最小属性为：

```yaml
educloud:
  common:
    environment: local
    id:
      enabled: false
      lease-ttl: 30s
      renewal-interval: 10s
      clock-backward-tolerance: 5ms
```

属性必须带配置元数据和启动期校验。Worker 槽位数固定为 32，不提供任意扩容开关。

Common 只使用最小 Spring/Jackson/Validation/SLF4J API；Redis、Security 和 Servlet 依赖保持可选或条件化。不得引入完整 Web Starter、MySQL、MyBatis-Plus、RabbitMQ、Nacos或业务服务依赖。

## 10. 运行数据流与失败策略

HTTP 流程：

```text
请求 → RequestContextFilter → 请求/MDC 上下文
     → Controller/Service → 响应工厂或异常处理器
     → 统一响应和 X-Request-Id → finally 清理 MDC
```

ID 流程：

```text
服务启动 → Redis 竞争槽位 → 周期续租 → 生成 ID
Redis/租约异常 → 生成器不可用 → 明确失败
```

事件和幂等流程：

```text
业务模块构造公共值对象
→ 业务模块自己的 Repository/Outbox 在后续 M 阶段持久化
```

所有失败采用显式、可观察、失败关闭策略，不以随机 Worker、内存幂等或静默吞异常进行虚假降级。

## 11. 测试矩阵

### 11.1 模块和依赖边界

- 父 POM 只新增 Common，Common 打包为普通 JAR。
- Java 17、21 可构建，输出目标为 Java 17。
- 不存在启动类、Controller、领域层或业务数据访问层。
- 依赖树不包含被禁止的 Starter、数据库、消息和注册中心依赖。

### 11.2 API、错误、请求和安全

- 固定 `Clock` 验证成功/失败响应和 UTC JSON。
- 分页验证零值、整除、余数、防御性复制和不可变性。
- Web 切片验证 400、401、403、409、429、500、503 及稳定错误码。
- 验证 500 不泄露内部信息，异常只记录一次。
- 验证请求 ID 生成、保留、替换、响应回传和 MDC 清理。
- 验证匿名/认证安全上下文及不可变集合，不从伪造身份头建立用户。

### 11.3 ID 单元和 Redis 集成

- 可控时钟验证毫秒内序列、跨毫秒复位、序列耗尽和时钟回拨。
- 内存仓储验证 32 槽位、第 33 个失败、租约丢失和所有者保护。
- 验证同一毫秒正常重启、线程暂停越过租约截止时间和异常退出后的槽位复用不会生成重复 ID。
- Rocky Docker 使用固定 Redis Testcontainer 验证真实原子获取、续租、带水位释放、TTL 到期和 Redis 中断。
- Testcontainer 使用随机环境命名空间，不干扰当前运行的 EduCloud Redis。

### 11.4 自动配置、事件和幂等

- `ApplicationContextRunner` 验证非 Web、Reactive、Servlet、Redis 禁用/启用和自定义 Bean 退让。
- 验证 AutoConfiguration Imports 可发现且非法属性启动失败。
- 验证事件信封必填字段和三类版本语义。
- 验证幂等相同摘要复用、不同摘要冲突；值对象不触发 I/O。

## 12. 验证与交付门禁

执行顺序为：

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common test
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify -Pintegration
mvn -f educloud-backend/pom.xml verify
```

M01 完成必须同时满足：

1. 每项实现先出现因目标行为缺失而失败的测试，再完成最小实现。
2. 单元、自动配置和 Redis 集成测试通过。
3. Java 21 Rocky 构建和全量 Maven 验证通过。
4. 数据库迁移标记 N/A：Common 不拥有数据库，不创建空迁移。
5. 服务启动标记 N/A：Common 是库，不伪造可运行服务。
6. 规格审查和质量审查没有未解决问题。
7. 变更只在 M01 允许范围，工作区干净。
8. 向用户汇报证据并等待确认；未经确认不进入 M02。

## 13. 非目标

- 不在 M01 实现真实认证、Gateway、安全路由或业务权限。
- 不在 M01 建立业务数据库、技术表、迁移执行器或消息发布器。
- 不在 M01 与前端联调，也不改变任何 Mock/真实能力状态标签。
- 不为未来未知需求增加万能工具类、跨服务 DTO 或领域枚举。
