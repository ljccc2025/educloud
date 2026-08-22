# EduCloud 学生端注册与注册限流设计规格

> 日期：2026-08-22
>
> 状态：已批准，待实现
>
> 模块：M03 边界内的前端认证联调补充（学生端）+ educloud-user 注册限流

## 1. 目的与范围

学生端（student-portal）当前登录页的「立即注册」按钮无功能；登录页预填 mock 假账号（limingxuan@educloud.com / 123456）误导用户；登录失败仅显示固定文案，吞掉真实错误。本设计补齐学生自助注册能力，并为注册接口增加生产级 Redis 限流，同时修复上述登录体验问题。

**范围（In Scope）**
- 学生端登录页「登录 | 注册」双 Tab（仅 student-portal，教师/管理端不动）
- 注册表单：用户名 + 邮箱 + 手机号 + 密码 + 确认密码 + 昵称（可选），字段规则与后端 RegisterStudentRequest 对齐
- 后端注册限流：IP + 设备双层 Redis 计数，超限 429 + Retry-After；Redis 不可用失败关闭 503
- 登录页修复：移除假账号预填；错误提示显示真实原因（apiErrorText）
- 测试与门禁回归

**非目标（Out of Scope）**
- 邮箱/短信验证（注册即生效，验证能力属后续模块）
- 图形验证码（限流已覆盖基础防刷；验证码可后续迭代）
- 教师/管理员自助注册（由后台创建账号）
- 修改 educloud-common（新错误码放 user 模块，避免动公共模块）

## 2. 现状与背景

- 后端 POST /api/v1/auth/register 已实现并验证（幂等键、唯一性、BCrypt、默认 STUDENT 角色、Outbox UserRegistered 事件），curl 实测 201。
- 前端 student-portal 已有 authApi.register（真实调用，已在联调改造中就绪），但登录页无注册表单入口。
- 登录页预填 limingxuan@educloud.com / 123456（代码 useState 默认值），后端无此用户；失败路径 else 分支固定文案。
- 前端 UI 体系：indigo 品牌区 + paper 背景 + display-heading/section-label/input-field/btn-primary/link-underline 等现有 class，注册页必须完全复用，不引入新风格。

## 3. 前端设计（student-portal）

### 3.1 页面结构

登录页（Login.tsx）改为双 Tab：
- Tab 1「登录」：现有登录表单（去掉假账号预填，输入框为空 + placeholder 提示）
- Tab 2「注册」：注册表单，同容器复用现有样式；底部「已有账户？去登录」切换
- Tab 切换保留各自输入状态，互不干扰

### 3.2 注册表单与校验

| 字段 | 必填 | 前端校验（与后端一致） |
|---|---|---|
| 用户名 | 是 | 3-32 位，regex [A-Za-z0-9_.\\-]+（连字符转义，避免 React pattern v 模式报错） |
| 邮箱 | 是 | 标准邮箱格式 |
| 手机号 | 是 | regex [0-9+ \\-]{5,32}（连字符转义） |
| 密码 | 是 | 8-128 位 |
| 确认密码 | 是 | 与密码一致 |
| 昵称 | 否 | ≤64 位；为空时注册请求不传（后端回退用户名） |

### 3.3 交互流程（已确认方案 B）

1. 提交注册 → 调 authApi.register(payload)（POST /api/v1/auth/register）
2. 成功 → 切回「登录」Tab，绿色提示「注册成功，请登录」，自动将用户名填入登录框（密码留空，用户手动输入）
3. 失败 → 红色提示条显示真实错误（apiErrorText：USERNAME_TAKEN / EMAIL_TAKEN / PHONE_TAKEN / PASSWORD_WEAK 等）

### 3.4 登录页修复

- 移除 useState('limingxuan@educloud.com') / useState('123456') 预填，改为空串 + placeholder
- 登录失败提示改为 apiErrorText(error) 真实原因（不再固定文案）
- 登录/注册按钮 loading 态、禁用态沿用现有样式

## 4. 后端设计（educloud-user 注册限流）

### 4.1 限流策略（双层）

| 维度 | Redis Key | 窗口 | 上限 |
|---|---|---|---|
| IP | educloud:{environment}:ratelimit:register-ip:{sha256(ip)} | 5 分钟 | 5 次 |
| 设备 | educloud:{environment}:ratelimit:register-device:{sha256(deviceId)} | 5 分钟 | 3 次 |

- 实现：Redis INCR + 首次 EXPIRE（原子窗口），任一层超限即拒绝
- 响应：429 + Retry-After（剩余秒数向上取整）+ 标准错误体（code=RATE_LIMITED）
- 失败关闭：Redis 异常时拒绝注册并返回 503 SERVICE_UNAVAILABLE（防绕过，与 M02 Gateway 限流语义一致）

### 4.2 设备标识

- 前端 http.ts 请求拦截器：首次访问生成匿名 deviceId（crypto.randomUUID）存 localStorage（key educloud_device_id），注册请求自动附加 X-Device-Id 头
- 后端按 sha256(deviceId) 计数，不落原始值；头缺失/非法时仅按 IP 限流

### 4.3 实现位置

- 新增 security/RegistrationRateLimitFilter（jakarta Servlet Filter，Order 高于业务过滤器；仅匹配 POST /api/v1/auth/register）
- 新增 config/RegistrationRateLimitProperties（前缀 educloud.user.registration.rate-limit）：
  - enabled（默认 true）、ip-max-attempts（5）、device-max-attempts（3）、window（5m）、redis-key-prefix（默认 educloud:{env}:ratelimit）
- 新错误码 RATE_LIMITED(429, "Too many registration attempts") 加入 UserErrorCode（不动 common）
- 429/503 响应由 Filter 直接写出（复用 ApiResponseFactory/对象序列化），不进入 Controller/GlobalExceptionHandler

### 4.4 过滤链顺序

RegistrationRateLimitFilter 位于业务处理之前（注册路径上最先执行），确保限流先于业务；permitAll 注册路径保持不变。

## 5. 错误处理

| 场景 | 前端提示 |
|---|---|
| RATE_LIMITED（429） | 「操作太频繁，请 N 秒后再试」（解析 Retry-After 动态显示） |
| SERVICE_UNAVAILABLE（503） | 「服务暂不可用，请稍后重试」 |
| 网络错误（status=0） | 「无法连接服务器，请检查网络」 |
| INVALID_CREDENTIALS 等登录错误 | 真实原因（用户名或密码错误/账号锁定/账号禁用） |
| 注册冲突（USERNAME_TAKEN 等） | 真实原因 |

apiErrorText 增加 RATE_LIMITED 与 SERVICE_UNAVAILABLE 两条映射。

## 6. 测试计划

| 层 | 用例 |
|---|---|
| 前端 | typecheck + build；表单校验（字段规则/密码一致性）、Tab 切换、注册成功→回登录+填用户名、429 文案 |
| 后端单元 | Filter：IP/设备计数、窗口过期、超限拒绝、Redis 异常→503、非注册路径放行、X-Device-Id 缺失降级 IP |
| 后端集成（VM） | RegistrationRateLimitIT（真实 Redis Testcontainer）：第 6 次 429 + Retry-After 存在；Redis 不可用→503 |
| 门禁回归 | user 模块 mvn verify（默认 + -Pintegration）、13 个 deploy 契约脚本、JDK17/21 双构建 |
| 手工验收 | 浏览器：注册成功→回登录→登录成功；连点 6 次注册→429 提示 |

## 7. 验收标准（Done）

1. 学生端注册 Tab 可完成真实注册，成功后回登录页并自动填用户名 + 绿色提示
2. 登录/注册错误提示为真实原因（不再出现固定假文案）
3. 同一 IP 5 分钟第 6 次注册返回 429，前端显示友好提示（含剩余秒数）
4. 注册页 UI 与学生端现有风格完全一致（复用现有 class 体系）
5. 全部测试/门禁通过；代码提交 git

## 8. 风险与备注

- 注册限流 Redis 与现有会话 Redis 共用实例；key 前缀与 Gateway 限流（educloud:{env}:ratelimit:*）同命名空间，但子键不同（register-ip/register-device），无冲突
- Gateway 默认透传未知请求头（X-Device-Id），无需改网关
- 限流参数为配置化，生产可按负载调整