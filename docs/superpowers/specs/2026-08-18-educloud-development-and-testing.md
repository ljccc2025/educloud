# EduCloud 开发、编码与测试规范

> 状态：`【目标设计】`
>
> 目标：贴合既有 Spring Boot/MyBatis-Plus 和 React/Vite 体系，不引入无必要框架。

## 1. 后端聚合工程

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
```

父 POM 统一 Java 17、Spring Boot 3.2.5、Spring Cloud 2023.0.3、Spring Cloud Alibaba 2023.0.1.0 及其他锁定依赖。子模块不得重复声明核心版本。

## 2. 服务包结构

保持现有规范的经典分层：

```text
com.educloud.<service>/
├─ controller/
├─ dto/
│  ├─ request/
│  └─ response/
├─ service/
│  └─ impl/
├─ mapper/
├─ entity/
├─ messaging/
├─ security/
├─ config/
├─ exception/
└─ support/
```

- Controller：协议转换、参数校验、权限入口，不写业务流程。
- DTO/VO：API 模型，不复用数据库 Entity。
- Service：业务规则、状态机、事务和 Outbox 写入。
- Mapper：MyBatis-Plus 数据访问，不拼装跨服务业务。
- Messaging：事件生产、消费和 DTO 版本适配。
- Config/Support：仅放基础设施适配，不形成“万能工具类”。

## 3. Common 模块边界

允许包含：

- 统一响应、分页结构和通用错误基类。
- `requestId/traceId` 处理。
- 安全上下文接口和通用认证过滤器基础。
- Outbox/Inbox 技术模板。
- 通用 Jackson、时间和 Bean Validation 配置。

禁止包含：

- User、Course、Order、Exam 等领域实体。
- 跨服务 Mapper、Repository 或业务 Service。
- 一个服务修改另一个服务数据的客户端包装。
- 不加边界的 `Utils` 集合。

Common 变更影响所有服务，必须保持向后兼容并通过全量构建。

## 4. Java 编码规则

- 类和方法表达领域含义，避免 `handleData`、`doSomething` 等模糊命名。
- 公共方法参数和返回值使用明确类型，不使用未约束 `Map<String, Object>`。
- 金额使用 `BigDecimal` 并明确舍入规则；禁止 `double/float`。
- 时间使用 `Instant` 或明确时区类型，数据库统一 UTC。
- 状态使用枚举并提供合法迁移方法，禁止散落字符串比较。
- 可空性必须明确；集合优先返回空集合而非 `null`。
- 不捕获异常后静默忽略；转换异常时保留根因用于服务端日志。
- 禁止在业务代码中硬编码密钥、服务地址、超时和环境分支。
- 不在循环中无界调用远端或执行 N+1 查询；头像、封面、附件、回放等当前页文件地址必须使用有上限的 File 批量授权，契约测试断言远端调用次数为 1。

## 5. Controller 与 DTO

- 所有写请求使用 Request DTO 和 Bean Validation。
- Path 中的 ID、Body 中的 ID 和当前用户关系必须校验。
- Controller 调用一次明确 Service 用例，不自行开启事务。
- Response DTO 只包含页面所需且允许暴露的字段。
- 密码、Token 哈希、密钥、内部对象键和隐藏答案永不进入响应 DTO。
- 列表接口统一分页结构；下载和支付回调按协议需要使用专用响应。

## 6. Service 与事务

- 事务放在 Service 公共方法，范围只覆盖必要数据库操作。
- 网络、文件上传和消息等待不放在数据库长事务内。
- 业务数据与 Outbox 事件在同一本地事务写入。
- 状态迁移先验证当前状态和版本，再执行副作用。
- 读方法按需要声明只读语义。
- 并发更新检查受影响行数；0 行不能当作成功。
- 跨服务失败使用事件补偿或明确恢复流程，不直接跨库回滚。

## 7. MyBatis-Plus 与 SQL

- Mapper 方法使用参数绑定和明确返回类型。
- 动态排序、表名和列名必须来自白名单。
- 查询只选择需要字段，分页接口不得先读取全表。
- 为外键列、唯一约束、状态和常用过滤/排序字段设计索引。
- 避免对索引列包函数导致索引失效。
- 批量操作限定批次，不能把不受限数组拼入 SQL。
- 生产慢查询必须有执行计划和数据规模证据后优化。

## 8. 错误和日志

- 业务失败抛出稳定领域异常，由全局处理器映射 HTTP。
- 未预期异常记录一次完整堆栈；上层不重复逐层打印。
- 日志使用参数化模板，不字符串拼接大型对象。
- 日志携带 `requestId/traceId`，敏感数据脱敏。
- 不使用 `System.out.println`，不向客户端返回异常类名。

## 9. API 与事件演进

- REST `v1` 内只做向后兼容增加；删除/改名需要新版本或迁移期。
- Event DTO 与数据库 Entity 分离。
- 事件包含 `eventVersion`，消费者明确支持版本范围。
- 生产者不能因新增字段要求旧消费者立即升级。
- OpenAPI、DTO、实现、测试和前端类型在同一任务同步更新。

## 10. 前端联调编码规则

- 页面组件不直接拼接后端 URL。
- 每个门户使用唯一 API Client 和统一响应解包。
- Access Token 只在内存，刷新逻辑集中管理。
- 401 只触发一次并发刷新；失败后清理会话。
- 所有写按钮处理提交中、成功、校验失败和冲突状态。
- 服务端错误码转换为可操作提示，未知错误保留 `requestId` 供排查。
- 当前 UI 类型与后端差异在 API 适配层转换，避免在所有组件中散落兼容代码。
- 学生端使用 pnpm；教师端、管理端使用 npm，不跨应用混用锁文件。

## 11. 测试层级

测试版本按既有规范固定为 JUnit 5.10、Mockito 5.11、Testcontainers 1.19、Playwright 1.47。后端版本由父 POM 管理，前端 E2E 依赖集中在独立 `educloud-frontend/e2e`，不使用个人临时目录作为 CI 依赖。

### 11.1 单元测试

目标：Service 规则、状态机、金额、截止时间、权限计算和事件构造。

- 不启动完整 Spring 容器。
- 固定时钟、ID 和外部适配器，测试结果可重复。
- 覆盖成功、边界、拒绝、重复、过期和并发冲突。

### 11.2 Web/安全切片测试

目标：Controller 校验、HTTP 状态、错误码、认证和权限。

- 缺 Token、过期 Token、错误角色、跨资源访问。
- 请求字段缺失、超长、非法枚举和错误 JSON。
- 响应不包含 Entity 敏感字段。

### 11.3 数据集成测试

目标：Mapper、唯一索引、乐观锁、事务和 SQL 兼容 MySQL 8.0。

- 不只使用与 MySQL 行为不同的内存数据库代替关键验证。
- 验证唯一约束、分页、排序、时间和金额精度。
- 迁移测试使用真实目标版本数据库环境。

### 11.4 消息测试

- 业务与 Outbox 同事务。
- 发布失败可重试。
- 重复事件只产生一次业务结果。
- 不支持版本进入死信并告警。
- 消费成功后才确认消息。

### 11.5 契约与集成测试

- OpenAPI 请求/响应与前端类型一致。
- Feign 调用超时和错误映射一致。
- 事件生产者和消费者对版本、必填字段达成契约。
- 外部支付、MinIO、邮件和媒体使用沙箱/受控替身，不伪造生产成功。

### 11.6 关键业务端到端测试

- 登录、刷新、退出和账号禁用。
- 课程创建、审核、发布、搜索和选课。
- 付费下单、重复回调、选课和退款。
- 课件权限、学习进度、作业提交/批改。
- 考试开始、保存、到期、交卷和评分。
- 直播权限、通知已读、社区幂等互动。
- 管理员跨域权限和敏感配置不回显。

Playwright 用例位于 `educloud-frontend/e2e/tests`，通过真实 Gateway 和受控测试基础设施执行。测试只在明确的 Mock/沙箱适配器上模拟外部支付、媒体和 AI，不 Mock 平台自己的业务 API。

## 12. 测试数据

- 测试使用明确的学生、教师、审核、财务和系统管理员账号。
- 时间敏感测试使用可注入时钟，不依赖真实等待。
- 每个测试创建并清理自己数据，不能依赖执行顺序。
- 支付渠道使用明确命名的测试适配器，生产环境默认禁用。
- AI Mock 和媒体 Mock 在 UI 和日志中标识，不混入真实能力指标。

## 13. 迁移测试

每个服务迁移至少验证：

1. 从空数据库升级到最新版本。
2. 从上一发布版本带代表性数据升级。
3. 重复执行保护或明确不可重复行为。
4. 新旧应用兼容窗口。
5. 回退、前滚或数据补偿步骤。
6. 索引、约束、字符集和时区。

没有 Docker/目标 MySQL 环境时，必须明确标注数据库迁移验证未完成。

## 14. 验证命令

后端全量：

```powershell
mvn -f educloud-backend/pom.xml verify
```

单服务及其依赖：

```powershell
mvn -f educloud-backend/pom.xml -pl educloud-user -am test
```

前端：

```powershell
Set-Location educloud-frontend/student-portal
pnpm run typecheck
pnpm run build

Set-Location ../teacher-portal
npm run typecheck
npm run build

Set-Location ../admin-portal
npm run typecheck
npm run build
```

端到端：

```powershell
Set-Location educloud-frontend/e2e
npm ci
npx playwright install --with-deps chromium
npx playwright test
```

只有实际执行并看到成功输出的命令才能在交付说明中写为“通过”。

## 15. 代码评审清单

- 服务边界和数据所有权是否正确。
- 权限与资源归属是否在服务端验证。
- 状态机是否阻止非法迁移和重复副作用。
- 金额、时间、ID 和并发是否安全。
- SQL 是否分页、参数化并有必要索引。
- 事务是否过大，是否包含网络调用。
- Outbox/Inbox、重试和补偿是否完整。
- 来源 `audit_event` 是否与业务变更同事务追加，且应用账号不能修改或删除。
- 错误码、日志、指标和审计是否覆盖。
- API、事件、迁移和前端适配是否同步。
- 测试是否覆盖失败路径，而不只覆盖正常流程。

## 16. Git 与交付

- 一次提交只包含一个可解释变更，使用现有 Conventional Commit 风格。
- 不把格式化整个仓库与功能变更混在一起。
- 不提交真实 `.env`、密钥、数据库数据目录、构建产物和日志。
- 发现无关未提交改动时保持原样，不暂存、不还原、不覆盖。
- 完成声明必须列出实际验证命令、未验证项和仍属 Mock/后续规划的能力。
