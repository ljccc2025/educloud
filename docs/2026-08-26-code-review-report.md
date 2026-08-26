# 代码审查报告（2026-08-26 全量复审）

- **审查对象**：`d:\microservice` 全代码库（11 个后端微服务 + 3 个前端门户 + 部署脚本）
- **审查日期**：2026-08-26
- **审查基线**：2026-08-24 审查报告（86 项）+ 2026-08-25 修复批次（16 项 P0/P1）
- **审查重点**：本次新增的 4 个模块（payment / notification / live / search，约 282 个 Java 文件）+ 旧报告修复状态抽查 + 前端修复状态抽查
- **严重级别定义**：P1 严重（资损/安全/数据一致性）｜P2 一般（竞态/可用性/健壮性）｜P3 建议（可维护性/防御性编码）

## 一、项目结构与技术栈

```
d:\microservice
├── educloud-backend/          # 11 个 Maven 模块（约 974 个 Java 文件）
│   ├── educloud-common        # 公共组件（雪花 ID/Outbox/异常/审计）
│   ├── educloud-gateway       # Reactive 网关（8080/8081）
│   ├── educloud-user          # 用户/认证/RBAC（8082/8083）
│   ├── educloud-file          # 文件/MinIO（8087/8088）
│   ├── educloud-course        # 课程中心（8089/8090）
│   ├── educloud-content       # 内容中心（8085/8086）
│   ├── educloud-order         # 订单中心（8091/8092）
│   ├── educloud-payment       # 支付中心（8093/8094）★新增
│   ├── educloud-live          # 直播中心（8095/8096）★新增
│   ├── educloud-notification  # 通知中心（8097/8098）★新增
│   └── educloud-search        # 搜索中心（ES）★新增
├── educloud-frontend/         # 3 个 React 应用（5173/5174/5175）
├── deploy/                    # docker-compose / sql 迁移 / 启动脚本
└── docs/ + 交接文档/            # 各模块交接与历史审查报告
```

**技术栈**：Java 17 / Spring Boot 3.2.5 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.1.0 / MyBatis-Plus 3.5.12 / RabbitMQ / Redis / MySQL 8 / MinIO / Elasticsearch 8.14 / OpenFeign；前端 React 18 + TS 5.5 + Vite 5.4 + Zustand + Tailwind。

## 二、审查范围与方法

1. **新增模块逐文件审查**：payment（90 文件）、notification（53）、live（77）、search（62），重点阅读控制器/服务/安全配置/MQ 消费/Outbox 等核心链路；
2. **旧报告修复状态抽查**：对 BUG-038/039/040/042/054/055/071/077 等在源码中逐条复核实锤；
3. **前端状态抽查**：学生端学习页权益锁定、结算轮询边界、教师端路由保护、管理端路由拆包；
4. **测试验证**：修改后 6 个受影响模块 `mvn test` 全量通过（user 110 + course 292 + order 83 + payment + notification 17 + search 67）。

## 三、本次新发现的问题（按优先级）

### P1 严重（3 项，均已修复）

**[P1-1] 支付回调安全审计随事务回滚丢失**（payment，已修复）
- 位置：`PaymentCallbackServiceImpl.handleCallback`
- 问题：签名校验失败/金额篡改等安全事件抛异常时，`@Transactional` 主事务回滚，`payment_callback_log` 的插入与 FAILED 状态更新一并回滚——**攻击尝试不留任何审计痕迹**，审计表形同虚设。
- 修复：回调日志落库与状态更新改为 `REQUIRES_NEW` 独立事务，主事务回滚不影响安全审计；单测新增断言"金额篡改后日志必须落库"。

**[P1-2] 迟付回调导致"已扣款但订单不可履约"资损**（payment+order，已修复）
- 位置：`PaymentCallbackServiceImpl.handleCallback`（入账前无业务订单可付性校验）；`OrderServiceImpl.processPaymentSuccess`（CAS 失败静默吞掉）
- 问题：支付回调在订单已关单/取消/过期后到达时，支付单仍被置 SUCCESS 并广播事件；order 消费端 CAS 失败仅打 INFO 日志返回，**学生已扣款但课程永不开通且无退款补偿**。
- 修复：
  - payment：入账前经 `OrderClient` 校验业务订单仍为 `PENDING_PAYMENT` 且未过期，不可付则支付单置 FAILED 并输出 `PAYMENT-ORDER-MISMATCH` ERROR 告警（fail-closed，查询失败按不可付处理），由日终对账+人工退款兜底；
  - order：CAS 失败时区分"已 PAID 幂等"与"其他状态"，后者 ERROR 告警（含 paymentOrderId/订单状态/过期时间）。

**[P1-3] 回调日志 INFO 级打印全量敏感参数**（payment，已修复）
- 位置：`PaymentCallbackController` 三个回调端点
- 问题：支付宝回调参数（买家/交易号/金额）、微信回调 headers+body 以 INFO 级全量打印，生产日志泄露交易敏感信息。
- 修复：降级为 debug 级别，审计职责移交 `payment_callback_log` 表。

### P2 一般（7 项，其中 6 项已修复）

**[P2-1] 邮件投递任务多实例重复发送**（notification，已修复）
- 位置：`DeliveryTaskJob` + `DeliveryTaskMapper`
- 问题：`selectPendingTasks` 后直接发送，无认领机制；多副本部署时同一邮件被多个实例重复发送。
- 修复：新增 `claimTask` CAS 认领（PENDING→SENDING），SENDING 超过 5 分钟可被重新认领（实例崩溃恢复）；失败重试时状态回置 PENDING。

**[P2-2] 领域事件消费失败后幂等键残留，事件永久不可重放**（notification，已修复）
- 位置：`DomainNotificationConsumer`
- 问题：处理异常仅记日志（消息被确认），但 Redis 幂等键已提前占用且 7 天不释放——事件既丢失又无法重放。
- 修复：异常时释放幂等键并告警，保留后续重放/补偿的可能性。

**[P2-3] 通知列表分页无上限**（notification，已修复）
- 位置：`NotificationServiceImpl.getMyNotifications`
- 问题：`size` 仅 `Math.max(1, size)`，`size=10000000` 可全量拉取（与 BUG-012 同型分页 DoS）。
- 修复：钳制为 1~100。

**[P2-4] 索引删除失败被静默标 PROCESSED，下架课程残留可搜**（search，已修复）
- 位置：`IndexSyncServiceImpl.handleCourseEvent`
- 问题：ES 删除文档失败仅 WARN 并标记 PROCESSED，消息被确认——**已下架/删除课程永久残留在 ES 索引**，用户搜索结果出现已下架课程。
- 修复：删除失败标记 FAILED 并抛异常，由消费者 NACK 路由到 `search.sync.dlq` 死信队列。

**[P2-5] 会话写入非原子，可产生无 TTL 会话键**（user，已修复，对应旧报告 BUG-039）
- 位置：`RedisSessionStore.writeActive`
- 问题：`putAll` + `expire` 两条命令非原子，崩溃窗口产生无 TTL 会话键，网关对该 sid 永久放行（fail-open）。
- 修复：改为单个 Lua 脚本原子执行 HSET + EXPIRE。

**[P2-6] 付费开课不校验课程终态**（course，已修复，对应旧报告 BUG-054）
- 位置：`EnrollmentService.enrollPaidCourse`
- 问题：对 ARCHIVED/DELETED 终态课程照常开课；课程不存在静默返回 null。
- 修复：终态课程拒绝开课并输出 `PAID-ENROLL-ARCHIVED-COURSE` ERROR 告警（不抛异常避免消息无限重投），缺失课程同样 ERROR 告警，由对账介入。

**[P2-7] 退款渠道调用失败状态不可恢复**（payment，**未修复，建议下迭代**）
- 位置：`RefundServiceImpl.auditRefund`
- 问题：`initiateRefund` 失败（含超时但渠道实际已退）直接把退款单置 FAILED；FAILED 无法重新审核（仅 APPLIED 可审），也无查单恢复任务——**资金与状态可能长期不一致**。
- 建议：失败时先调 `queryPayment` 消除二义性；无法确认时保持 PROCESSING 并由定时任务查单收敛；或允许 FAILED 退款单进入人工复核工作台。

### P3 建议（本次已顺手修复 4 项）

- **[P3-1] InternalApiFilter 内部令牌非常量时间比较**（payment，已修复）→ `MessageDigest.isEqual`。
- **[P3-2] Redis 回调锁无属主令牌**（payment，已修复）→ 锁值带 UUID，Lua 原子比对删除，防处理超时后误删他人锁。
- **[P3-3] `createCashierPayment` 长事务内嵌 Feign + 渠道 HTTP**（payment，未修复）→ 与 BUG-019 同反模式，渠道下单移出事务（下迭代）。
- **[P3-4] PaymentExceptionHandler 无兜底异常**（payment，未修复）→ 补 `@ExceptionHandler(Exception.class)` 统一 500 结构。
- **[P3-5] 退款单 PROCESSING 卡死无收敛任务**（payment，未修复）→ 定时查单恢复。
- **[P3-6] 直播弹幕无频率/长度限制**（live，未修复）→ WebSocket 消息层限流。
- **[P3-7] 直播开播通知硬编码 fe_demo_10**（notification，未修复）→ 应通知报名学生。
- **[P3-8] `DEMO_BROADCAST_USERS` 硬编码 + 全员广播只发 4 人**（notification，未修复）→ 生产需接真实用户表。
- **[P3-9] 邮件接收目标硬编码虚构地址**（notification，未修复）。
- **[P3-10] `handleLiveStarted` 等消费端 catch 后不区分可重试性**（notification，未修复）→ 配置 DLQ + 重试容器。
- **[P3-11] 回调日志 `calculateHash` 失败返回时间戳伪哈希**（payment，未修复）→ 影响去重，建议失败即告警。
- **[P3-12] 搜索 DLQ 无消费者/重放任务**（search，未修复）→ 死信堆积无处理路径，建议增加 DLQ 重放任务或告警。

## 四、旧报告（2026-08-24，86 项）修复状态抽查

| 项 | 状态 | 说明 |
|---|---|---|
| BUG-001~006（content P0/P1） | ✅ 已修复 | 2026-08-25 批次，本次复核无误 |
| BUG-016~022（order P0/P1） | ✅ 已修复 | 含 Outbox/兜底扫描/金额重算，本次复核无误 |
| BUG-034~037（user P1） | ✅ 已修复 | 锁定语义/GRACE 过期/bootstrap 空串/撤销降级 |
| BUG-051~053（course P1） | ✅ 已修复 | 重试容器/pre_submit_lifecycle/空值清列 |
| BUG-038 JwtDecoder 不校验 audience | ⚠️ 未修复 | user `JwtValidators.createDefaultWithIssuer` 仍无 aud 校验（P2） |
| BUG-039 Redis 会话非原子 | ✅ **本次已修复** | Lua 原子写入 |
| BUG-040/071/077 真实 IP 未注入/剥离清单 | ⚠️ 未修复 | 网关仍不注入 X-Real-Ip、未剥离 x-real-ip（P2/P3） |
| BUG-042/058/064 Outbox 无 SKIP LOCKED | ⚠️ 部分修复 | 新模块已用 CAS 抢占（payment/search），旧模块 order/user/course/file 仍为整批单事务（P2） |
| BUG-054 付费开课终态校验 | ✅ **本次已修复** | ARCHIVED/DELETED 拒绝 + 告警 |
| BUG-055 选课计数不校验影响行数 | ⚠️ 未修复 | 付费路径（P3） |
| BUG-060 分类树递归环路 | ⚠️ 未验证 | 建议下迭代补防环 |
| BUG-072 直播 WS 鉴权 | ✅ 已修复 | ticket 方案（live 模块已实现） |
| BUG-078 无 TTL 会话键判 CORRUPT | ⚠️ 未修复 | 与 BUG-039 联动，现 BUG-039 已修，此问题自然收敛 |
| 其余 P2/P3（约 50 项） | ⚠️ 未修复 | 见旧报告，按迭代消化 |

## 五、前端审查状态（对应 2026-08-24 前端交接 10 项）

| # | 问题 | 状态 |
|---|---|---|
| 1 | 未购买课程可访问学习地址 | ✅ 已修复（学习页 `isLessonLocked` 付费锁定 UI + 后端 BUG-002 服务端校验） |
| 2 | 订单无归属字段 | ✅ 已修复（真实后端订单含 studentId，服务端有归属校验） |
| 3 | 教师端路由无保护 | ✅ 已修复（ProtectedRoute 已接入全部业务路由） |
| 4 | 空评分保存为 0 分 | ⚠️ 未验证（建议回归） |
| 5 | 支付确认轮询无边界 | ✅ 已修复（maxAttempts=20 + clearInterval） |
| 6 | pnpm esbuild 占位配置 | ⚠️ 未验证 |
| 7 | 顶部搜索未生效 | ⚠️ 未验证 |
| 8 | 支付/优惠文案夸大 | ⚠️ 未修复（首页仍宣传"首单立减 50 元"） |
| 9 | 持久化状态无结构校验/无测试 | ⚠️ 未修复 |
| 10 | 管理端首屏包体过大 | ✅ 已修复（React.lazy + Suspense 拆包） |

## 六、本次修复清单（19 个文件，含 3 个测试）

| 模块 | 文件 | 修复内容 |
|---|---|---|
| payment | `PaymentCallbackServiceImpl.java` | 审计独立事务 / 迟付防护 / 锁属主令牌 |
| payment | `InternalApiFilter.java` | 常量时间比较 |
| payment | `PaymentCallbackController.java` | 回调日志降级脱敏 |
| payment | `PaymentCallbackServiceTest.java` | 适配新构造与审计断言 |
| payment | `PaymentFlowIntegrationTest.java` | 适配新构造 |
| order | `OrderServiceImpl.java` | 支付履约 CAS 失败告警（区分幂等） |
| notification | `DeliveryTaskMapper.java` | CAS 认领 + SENDING 崩溃恢复 |
| notification | `DeliveryTaskJob.java` | 认领后发送，失败回置 PENDING |
| notification | `DomainNotificationConsumer.java` | 失败释放幂等键 + 双队列监听（支付事件） |
| notification | `NotificationServiceImpl.java` | 分页钳制 1~100 |
| notification | `DeliveryTaskJobTest.java` | 适配认领逻辑 |
| notification | `RabbitMqConfiguration.java` | 绑定支付交换机 payment.succeeded/refunded |
| search | `IndexSyncServiceImpl.java` | 删除失败→FAILED+死信 |
| user | `RedisSessionStore.java` | Lua 原子写入（BUG-039） |
| course | `EnrollmentService.java` | 付费开课终态校验（BUG-054） |
| 前端 student | `useNotificationStore.ts` | 移除写死种子数据，接入真实通知 API |
| 前端 student | `Notifications.tsx` | PAYMENT 分类 + 挂载拉取 + 加载/空态 |
| 前端 student | `types.ts` | NotificationKind 补充 PAYMENT；id 改 string |
| 前端 student | `Navbar.tsx` | 登录后拉取真实未读数（铃铛角标） |

**验证**：受影响 6 模块 `mvn test` 全量通过（BUILD SUCCESS），编译零错误；前端 `tsc --noEmit` 零错误。

## 七、浏览器 E2E 验证补充发现（M10 通知链路，已修复并验证）

MCP 浏览器自动化验证购课链路时发现“课程购买成功”通知从未到达学生端，排查后定位为**通知事件链路断裂**，分为后端与前端两处缺陷：

**[BUG-A] 通知消费端未绑定支付交换机（后端，已修复）**
- 现象：购课后订单/开课均正常，但 `/api/v1/notifications` 只有种子数据；`rabbitmqctl list_bindings` 显示 payment 只绑定了 order/course 队列。
- 根因：payment 服务事件发往 `educloud.payment.exchange`（`payment.succeeded`/`payment.refunded`），而 notification 仅监听 `educloud.events` 总线；且 `order.refunded`/`live.started`/`assignment.graded` 全代码库无生产者（4 条通知链路全部断裂，属 M10 验收缺陷）。
- 修复：`RabbitMqConfiguration` 新增 `educloud.notification.payment-events` 队列并绑定支付交换机两条路由；`DomainNotificationConsumer` 双队列监听，支付事件无 `eventId` 时用 `routingKey + aggregateId` 合成幂等键，兼容 `amountCents`（分）→ `amount`（元）转换。
- 验证：购课 Python 3.12（¥168）后 1 秒内事件被消费，`sys_notification` 落库、邮件投递任务发出、学生端通知中心展示“课程购买成功”。

**[BUG-B] 学生端通知中心为写死数据，永不请求后端（前端，已修复）**
- 现象：学生端 `/notifications` 始终展示 6 条固定种子（作业/直播/考试…），后端真实数据不展示。
- 根因：`useNotificationStore.ts` 内写死 `initialNotifications`，无任何 API 调用；且 `NotificationKind` 缺少 PAYMENT 枚举。
- 修复：Store 改为调用真实 API（乐观更新已读），页面挂载拉取 + 加载态，Navbar 登录后拉取真实未读数，补充 PAYMENT 分类展示。
- 验证：页面展示真实 5 条通知（含“课程购买成功”），未读数 2→1 正确。

**[BUG-C] Snowflake ID 前端 Number 精度丢失（前端，已修复）**
- 现象：标记已读请求 `PUT /notifications/2092160723423785000/read` 返回 404（真实 ID 2092160723423784961）。
- 根因：`Number(dto.id)` 将 Snowflake 字符串转 Number 丢失精度（超过 `Number.MAX_SAFE_INTEGER`），后端刻意用字符串序列化 ID。
- 修复：`StudentNotification.id` 改为 string，全程透传不做数值转换。
- 验证：请求 `PUT /notifications/2092160723423784961/read` 返回 200，DB `is_read=1, read_at` 落库。

## 八、遗留事项（按优先级）

1. **[建议] 退款状态机恢复**（P2-7）：渠道查单 + PROCESSING 收敛任务。
2. **[建议] BUG-042/058/064**：user/course/file/order 旧 Outbox 中继改造为逐条 CAS + SKIP LOCKED。
3. **[建议] BUG-038/040/071/077**：JWT audience 校验、网关真实 IP 注入与剥离清单。
4. **[建议] search DLQ 重放任务**：死信队列消费 + 重试/告警。
5. **[建议] 直播弹幕限流、通知硬编码受众替换**、首页优惠文案修正。
6. **[必须] 提交 Git**：本批次 19 个文件变更需提交推送（沿用中文约定式提交规范）。
