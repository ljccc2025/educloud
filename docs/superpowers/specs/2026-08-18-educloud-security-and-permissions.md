# EduCloud 认证、权限与安全设计

> 状态：`【目标设计】`
>
> 当前风险：三套门户认证均为 Mock，且实现方式不一致。

## 1. 安全目标

- 身份由服务端验证，任何前端状态都不能构成授权证据。
- 用户只能访问角色允许且属于自己的业务资源。
- 密钥、密码、Token、支付凭据和对象存储凭据不进入普通 API 响应。
- 登录、授权、审核、交易、成绩和配置变更具有完整审计证据。
- 外部回调、文件上传和 WebSocket 连接具有独立的验证边界。

## 2. 当前问题

| 当前行为 | 风险 |
|---|---|
| 任意非空账号密码可登录 | 没有真实身份验证 |
| 学生和管理 Token 存在 localStorage | XSS 可读取长期凭据 |
| 教师端 Token 只在内存且无路由保护 | 刷新丢失，页面可越过登录 |
| 管理端表单回显 SMTP、MinIO、JWT 密钥 | 浏览器和日志可能暴露凭据 |
| 前端根据 Token 是否存在判断权限 | 可伪造且无法表达资源归属 |

这些能力统一标记为 `【前端 Mock】`，不能作为后端安全基线复用。

## 3. 认证流程

### 3.1 登录

1. Gateway 对登录接口执行 IP 与账号维度限流。
2. User 服务读取账号并使用恒定语义的失败响应，避免枚举用户。
3. 校验账号状态、锁定时间和密码哈希。
4. 创建服务端 Refresh 会话，数据库保存 Token 哈希，Redis保存活跃/撤销状态。
5. 返回短期 Access Token；Refresh Token 写入 HttpOnly Cookie。
6. 记录成功或失败登录审计，不记录原始密码。

### 3.2 Token 策略

| 项目 | 规则 |
|---|---|
| Access Token | 默认 15 分钟，可配置；只保存在前端内存 |
| Refresh Token | 默认 7 天，可配置；HttpOnly、Secure、限定 Path；数据库每个轮换 Token 一行 |
| 签名 | 使用非对称密钥；私钥只在 User 服务，Gateway/服务持有公钥 |
| Key ID | JWT Header 包含 `kid`，支持平滑轮换 |
| 撤销 | 登出、改密、禁用、角色敏感变更时撤销会话 |
| 轮换 | `sid` 表示会话族；每次刷新原子消费父 Token 并插入子 Token |
| 重用检测 | 并发宽限窗口外再次使用已轮换 Token 时撤销整个 `sid/family_id` 并记录安全审计 |

Access Token 至少包含 `sub`、`sid`、`userType`、角色/权限摘要、`iat`、`exp`、`iss`、`aud` 和 `tokenVersion`。不得包含手机号、邮箱、密码或其他不必要个人信息。

刷新事务按 Token 哈希锁定当前 `refresh_session` 行：只有 `ACTIVE` 且未过期的 Token 可以从 `ACTIVE` 原子迁移为 `ROTATED` 并创建一个子 Token。并发请求中首个成功，其余在短并发宽限窗口内返回 `REFRESH_ALREADY_ROTATED`，不误判为攻击；窗口外或不同客户端指纹重用时撤销整个会话族。前端必须单飞刷新，不能用第二个请求覆盖首个响应 Cookie。

### 3.3 在线撤销语义

- User 在注销、改密、禁用账号或角色敏感变更时撤销相关 `sid`，并在 Redis 写入撤销/版本状态直到 Access Token 自然过期。
- Gateway 对每个受保护请求校验 `sid` 和 `tokenVersion`；可以使用秒级本地正缓存，但禁用和高风险变更必须主动失效。
- Redis 无法确认会话状态时，公开只读接口仍可工作，受保护写操作和管理/交易接口必须失败关闭；不能因依赖故障继续接受可能已撤销的 Token。
- Access Token 最长剩余有效期决定撤销记录的最短保留期。
- User 服务的权限或状态变更在数据库事务提交后发布失效事件，Gateway 丢失事件时仍以 Redis 权威撤销状态为准。

### 3.4 Cookie、CORS 与 CSRF

- 生产 Refresh Cookie 使用 `Secure` 和 `HttpOnly`，`SameSite` 根据实际域名拓扑选择最严格可用值。
- 刷新和注销接口验证 `Origin`，只允许明确的学生、教师、管理端域名。
- 携带凭据的 CORS 不能配置通配来源。
- 普通业务写请求使用 Bearer Access Token，不依赖 Cookie 身份。
- 开发环境只开放 `5173/5174/5175`，不使用全网段通配。

## 4. 密码和账号保护

- 使用 Spring Security 支持的自适应密码哈希，不保存或记录明文密码。
- 密码最小长度、失败锁定次数和锁定时间为服务端安全配置，不由普通管理页面回显密钥。
- 登录失败响应不区分账号不存在和密码错误。
- 找回/重置密码为 `【后续规划】`。落地时必须使用一次性、短期、不可重复令牌并补齐数据表、通知、页面和滥用防护；首期不提供重置端点。
- 账号禁用后立即阻止刷新，并撤销已知会话。
- 管理员不能读取或重置为自定义明文密码；重置操作产生一次性流程并审计。

## 5. RBAC 角色

角色是权限集合模板，最终授权依据是权限码和资源归属：

| 角色 | 典型范围 |
|---|---|
| `STUDENT` | 学习、购买、提交、考试、通知和社区 |
| `TEACHER` | 自有课程、内容、直播、作业、考试和学生分析 |
| `COURSE_REVIEWER` | 课程审核 |
| `CONTENT_REVIEWER` | 课件和社区内容审核 |
| `FINANCE_ADMIN` | 订单、支付、退款和对账 |
| `SYSTEM_ADMIN` | 用户状态、角色权限、公开平台配置和审计查询 |
| `SUPER_ADMIN` | 经严格审计的全局管理；不等于可以读取密钥或密码 |

不依赖角色名称硬编码所有判断。内置角色可以固定编码，自定义角色通过权限表组合。

## 6. 权限码目录

### 用户与平台

- `user:read`、`user:status:update`
- `rbac:read`、`rbac:manage`、`rbac:assign`
- `platform:config:read`、`platform:config:update`
- `security:key-status:read`
- `audit:read`

### 课程与内容

- `course:create`、`course:update`、`course:submit`
- `course:audit`、`course:offline`、`course:republish`、`course:archive`
- `course:student:read`、`course:enroll`
- `content:manage`、`content:audit`
- `assignment:manage`、`assignment:submit`、`assignment:grade`
- `exam:manage`、`exam:attempt`、`exam:grade`
- `community:publish`、`community:moderate`

### 交易与支撑

- `order:read:self`、`order:read:any`
- `refund:request`、`refund:review`
- `payment:read`、`payment:reconcile`
- `live:manage`、`live:join`、`live:moderate`
- `file:upload`、`file:read`、`file:delete`、`file:storage:status:read`、`file:storage:test`
- `notification:read:self`、`notification:manage`、`notification:channel:status:read`、`notification:channel:test`
- `analytics:teacher:read`、`analytics:admin:read`
- `search:manage`、`recommendation:manage`

权限码只增不随意改名。废弃权限需要迁移角色关系并记录版本。

## 7. 资源归属规则

| 资源 | 授权规则 |
|---|---|
| 用户档案 | 本人可读写本人允许字段；管理员按权限读取脱敏信息 |
| 课程 | 负责人或共同授课教师可编辑；审核角色不能审批自己的提交 |
| 审核快照 | 提交教师只能查看自己的提交；具备审核权限者查看职责范围；公开接口永不返回草稿快照 |
| 课件 | 课程教师可维护；学生需有效选课或免费预览权限 |
| 作业提交 | 学生只操作自己的提交；课程教师可查看和评分 |
| 考试答卷 | 学生只访问自己的答卷；教师仅访问自己课程，答案按阶段隐藏 |
| 订单和支付 | 学生只访问本人交易；财务按权限访问，普通管理员不可越权 |
| 直播 | 课程教师管理；已选学生进入；聊天身份来自认证上下文 |
| 通知 | 用户只访问自己的收件箱记录 |
| 文件 | 访问权限由绑定的业务资源决定，知道 `fileId` 不等于有权限 |
| 社区 | 本人编辑，审核员隐藏；删除和审核均记录原因 |

无权用户访问敏感资源时，可以按资源类型返回 404，避免泄露资源存在性。

## 8. Gateway 与服务安全

- Gateway 校验 JWT 基础属性并移除客户端伪造的内部头。
- 各业务服务再次校验 Token 或受信身份上下文，并执行方法权限和资源归属。
- 代表用户的内部调用传递原始、受验证 Access Token 和追踪信息。
- User 是内部服务 Token 的唯一签发者。服务通过只在 Secret 中保存的 `client_id/client_secret` 调用内部签发端点，Secret 在 User 数据库只保存哈希。协议使用 HTTPS、HTTP Basic 客户端认证和 `client_credentials` 表单，不允许凭据出现在 URL、日志或追踪标签；非本地环境拒绝明文交换。
- 服务 Token 默认 5 分钟，使用同一非对称签名体系，包含 `sub=service:<name>`、`clientId`、`aud`、`scope`、`jti`、`iat/exp` 和 `tokenVersion`；不得携带终端用户权限。
- 调用方为每个目标服务申请精确 `aud`，目标服务同时检查签名、受众、scope 和接口调用方 ACL。例如销售快照只允许 Order，订单支付快照只允许 Payment。
- Feign 客户端只在内存缓存到期前的服务 Token；轮换服务 Secret 时支持短期双凭据并撤销旧版本。
- 服务客户端注册/轮换不是产品 API。受控 CLI/一次性 Job 从 stdin 或挂载 Secret 文件读取原始值，以 `SERVICE_BOOTSTRAP_JOB` 主体调用 User 领域服务写入哈希和审计；命令行参数、stdout 和日志不得出现 Secret。相同 `clientId + secret` 重跑幂等，不同 Secret 必须显式 `rotate`，灾备不匹配时递增 `tokenVersion` 并执行全量受控轮换。
- 本地 Compose 通过 `.env` 注入独立服务凭据，生产由 Kubernetes Secret 注入；均不提交真实值。
- `/internal/v1` 只在内部网络可达且不经外部 Gateway，仍必须完成上述服务身份验证。
- 非本地环境的服务间链路使用应用 HTTPS：证书与受信 CA 从 Kubernetes Secret 挂载并支持轮换；“在集群内”不能替代传输加密。Compose 仅允许隔离网络内的明确开发模式使用 HTTP 和开发凭据。
- Actuator 只公开必要健康和指标端点，敏感管理端点不对公网开放。

## 9. 敏感配置

### 不允许返回前端

- JWT 私钥或 HMAC Secret。
- SMTP 密码。
- MinIO Access Key 和 Secret Key。
- 支付渠道私钥、Webhook Secret。
- 数据库、Redis、RabbitMQ 密码。
- AI 模型供应商 Key。

管理端只能看到连接状态、脱敏标识、更新时间和最后测试结果。产品 API 不读取或写入 Secret：生产密钥更新只通过 Kubernetes Secret/CI 受控流程，本地只通过未提交的 `.env`；响应永不回显原值。连接测试必须限频、审计且使用服务当前已加载凭据，测试请求也不能携带密钥。

### 配置来源

- 公开业务配置：User 服务数据库。
- 普通运行配置：Nacos 或 ConfigMap。
- 密钥：环境变量或 Kubernetes Secret。
- 本地示例：只提交占位 `.env.example`，真实值进入忽略文件。

## 10. 输入、输出与内容安全

- Controller 使用 Bean Validation 限制长度、范围、枚举和必填字段。
- Mapper 只使用参数绑定；动态排序和字段名使用白名单。
- React 默认转义文本；若未来支持富文本，服务端和展示端都必须采用允许列表清理。
- 外部 URL 只允许符合协议和业务规则的地址，禁止服务端无约束抓取任意内网 URL。
- 错误响应不包含 SQL、堆栈、对象存储路径和内部主机信息。
- 导出和批量查询设置最大范围，避免资源耗尽。

## 11. 文件安全

- 服务端生成对象键，文件名不参与路径拼接。
- 同时校验扩展名、声明类型、实际媒体类型和大小。
- 对课件、头像、封面设置独立大小和类型白名单。
- 不提供只凭 `fileId` 的公共下载签名。领域服务先校验本人/课程/选课/可见性，再以服务 Token 调 File 内部单文件或有界批量 `download-grants`；File 从已认证 `clientId` 推导 ownerService，不信任请求体伪造值，并逐项校验 `(ownerService,ownerType,ownerId,fileId)` 精确绑定、purpose 和终端主体。仅公开目录等登记场景可使用匿名主体；下载地址短期有效，私有 Bucket 不允许匿名直读。
- 头像、课程封面、作业附件和回放列表必须每页至多一次批量签名，禁止逐 DTO 远端 N+1。短期地址不落业务库、不进事件/日志、不缓存超过 `expiresAt`；退款、选课撤销或权限变化后不能刷新新地址，已经签发的短 TTL 是明确的最大撤销窗口。
- 可执行文件、脚本和超出允许范围的压缩包默认拒绝。
- 上传完成前文件状态不可用；未绑定文件定期清理。
- 删除文件先检查绑定，强制删除需要高权限和审计。

## 12. 支付与外部回调安全

- 回调端点不使用用户 JWT，而是验证渠道签名、时间、商户、金额、币种和交易号。
- 保存原始载荷哈希和渠道通知 ID，用唯一索引抵御重复回调。
- 记录回调时屏蔽密钥和敏感支付信息。
- 对账差异不能由普通接口自动改写订单；需要明确补偿命令和审计。
- 模拟支付必须使用单独渠道编码和非生产环境开关，生产默认关闭。

## 13. WebSocket 与直播安全

- 浏览器先通过带 Access Token 的 HTTPS 接口申请一次性短期连接票据；票据绑定用户、直播间、过期时间和随机数。
- WebSocket 握手只接受该票据，不接受查询参数中的任意用户 ID；验证成功后立即消费，重复使用失败。
- 票据必须避免进入访问日志、错误响应和前端持久化存储。
- 加入房间时验证课程选课或教师归属。
- 限制消息长度、频率和支持的消息类型。
- 服务端写入真实发送者 ID 和时间。
- 禁言、撤回、踢出等动作需要直播管理权限并审计。
- 断线重连不能绕过已结束直播或被撤销权限。

## 14. 日志与隐私

- 密码、Token、Cookie、密钥和完整支付载荷不写日志。
- 手机号、邮箱、IP 等按使用目的最小化并脱敏。
- 管理端数据导出、用户状态、权限和成绩变更必须审计。
- 审计记录只追加，不允许普通管理员修改或清空。
- 首期只交付“禁用/恢复用户”，不提供删除用户 API。个人数据保留、匿名化、法定留存冲突处理和不可逆执行审批必须在上线前依据适用要求形成专项方案；完成该方案、数据依赖清单、双人复核和恢复演练前，管理端删除入口必须移除或明确显示 `【后续规划】`。

## 15. AI 安全边界

- 当前 AI 助手为 `【前端 Mock】` 或显式配置的外部端点，不代表平台已有模型治理。
- 浏览器不得保存模型密钥，也不得直接把全量用户资料、作业或私密课程内容发送给供应商。
- 真实集成前必须确定模型供应商、数据范围、提示注入防护、内容审核、费用上限、留存策略和人工申诉流程。
- AI 输出不能自动评分、审批、退款、封禁账号或执行不可逆业务动作。

## 16. 安全验收

- 未认证、无权限、跨用户、跨课程和伪造身份头测试通过。
- Token 过期、撤销、刷新轮换和重用检测通过。
- 密钥不出现在 API、日志、前端构建产物和 Git 中。
- 支付回调验签、金额不一致、重复通知测试通过。
- 文件越权、类型伪造、超限和未绑定访问测试通过。
- 管理员自审、教师访问他人课程、学生读取他人答卷均被阻止并记录必要审计。
