# M08 educloud-payment（支付中心与多渠道收银台）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建并交付 EduCloud 支付中心微服务 `educloud-payment`（端口 8093/8094），完成多渠道收银台（Mock 沙箱、支付宝 EasySDK、微信支付 V3）、异步回调安全验签与防重放、严格 CAS 支付与退款状态机、Transactional Outbox 可靠事件广播与下游闭环、日终对账与 4 类差错平账中心，以及前端三端收银台与退款/对账管理集成。

**架构：** 基于 Spring Boot 3.2.5 + MyBatis-Plus + Nacos + Redis + RabbitMQ 双端口微服务架构；业务数据存储在独立逻辑库 `educloud_payment`；基于统一 SPI（`PaymentChannelPlugin`）实现渠道解耦；基于 Transactional Outbox + Outbox Relay 实现支付与退款事件可靠投递；双向对账引擎比对渠道与本地流水生成差错并闭环处置。

**技术栈：** Java 17, Spring Boot 3.2.5, Spring Cloud Alibaba (Nacos), MyBatis-Plus 3.5.12, MySQL 8.0, Redis, RabbitMQ (AMQP), Alipay EasySDK, WeChatPay V3 API, OpenFeign, React 18, TypeScript, Tailwind CSS, Vite.

---

## 文件结构与模块规划

```
educloud-backend/
├── pom.xml                                                 # 注册 educloud-payment 模块
└── educloud-payment/
    ├── pom.xml                                             # 模块依赖 (Web, Security, Redis, AMQP, MyBatis-Plus, Feign, SDK)
    └── src/
        ├── main/
        │   ├── java/com/educloud/payment/
        │   │   ├── PaymentApplication.java                 # 启动类 (端口 8093/8094)
        │   │   ├── config/
        │   │   │   ├── IdentifierConfig.java               # 64 位雪花 ID 发生器
        │   │   │   ├── HealthIndicatorConfig.java          # MySQL/Redis/RabbitMQ/Nacos 深度健康检查
        │   │   │   ├── MybatisPlusConfig.java              # 分页与 @Version 乐观锁插件
        │   │   │   ├── RabbitPaymentConfig.java            # TopicExchange 与死信/重试队列定义
        │   │   │   ├── RedisConfig.java                    # Redis 序列化与分布式排他锁配置
        │   │   │   ├── SecurityConfig.java                 # OAuth2 JWT 解码器与权限拦截
        │   │   │   ├── InternalApiFilter.java              # 内部 RPC Header 校验过滤器
        │   │   │   └── PaymentProperties.java              # 渠道凭据与沙箱环境配置映射
        │   │   ├── controller/
        │   │   │   ├── PaymentCashierController.java       # 学生收银台下单、轮询、Mock确认
        │   │   │   ├── PaymentCallbackController.java      # 第三方渠道异步 Webhook 回调接收入口
        │   │   │   ├── PaymentRefundController.java        # 退款申请、列表与审核原路退款
        │   │   │   └── ReconciliationController.java       # 日终对账触发、批次查询与差错平账
        │   │   ├── dto/
        │   │   │   ├── request/                            # CashierPayRequest, RefundApplyRequest, DiffResolveRequest
        │   │   │   └── response/                           # CashierPayResponse, PaymentDetailResponse, ReconcileBatchResponse
        │   │   ├── entity/                                 # PaymentOrder, PaymentTransaction, PaymentCallbackLog, PaymentRefund, ReconciliationBatch, ReconciliationDiff, OutboxEvent
        │   │   ├── enums/                                  # PaymentStatus, RefundStatus, PaymentChannel, DiffType, ResolveAction
        │   │   ├── exception/                              # PaymentErrorCode, PaymentException, GlobalExceptionHandler
        │   │   ├── feign/
        │   │   │   ├── OrderClient.java                    # 调用 educloud-order 查验可付性与快照
        │   │   │   └── dto/OrderPayableSnapshotResponse.java
        │   │   ├── mapper/                                 # 各实体 MyBatis-Plus Mapper 接口
        │   │   ├── messaging/
        │   │   │   ├── OutboxEventWriter.java              # 本地事务同库写入 Outbox
        │   │   │   ├── PaymentOutboxRelay.java             # 定时抢占式投递与指数退避重试
        │   │   │   └── events/                             # PaymentSucceededEvent, PaymentRefundedEvent
        │   │   ├── service/
        │   │   │   ├── PaymentService.java                 # 支付核心服务
        │   │   │   ├── RefundService.java                  # 退款与审核服务
        │   │   │   ├── ReconciliationEngine.java           # 日终双向对账比对与差错识别
        │   │   │   └── impl/                               # 对应实现类
        │   │   └── spi/                                    # 支付通道 SPI 抽象体系
        │   │       ├── PaymentChannelPlugin.java           # 核心统一契约接口
        │   │       ├── PaymentChannelFactory.java          # 插件工厂路由
        │   │       ├── model/                              # UnifiedPayResult, CallbackVerifyResult, UnifiedRefundResult, ChannelBillItem
        │   │       └── plugins/
        │   │           ├── MockPaymentPlugin.java          # 本地自闭环沙箱插件
        │   │           ├── AlipayEasySdkPlugin.java        # 支付宝 RSA2 插件
        │   │           └── WeChatPayV3Plugin.java          # 微信支付 V3 证书与 AEAD 插件
        │   └── resources/
        │       ├── application.yml                         # 端口、数据源、Redis、RabbitMQ、Nacos 配置
        │       └── mapper/                                 # XML Mappers
        └── test/                                           # TDD 单元测试与切片测试

deploy/sql/payment/
├── V000__technical_tables.sql                             # Outbox 发件箱表
├── V001__init_payment_schema.sql                           # 支付主单、流水、回调、退款、对账表
└── V002__payment_seed_data.sql                             # 预置初始测试数据

deploy/sql/user/
└── V008__payment_permissions.sql                           # 注入 refund:admin, reconciliation:admin 权限
```

---

## 任务拆解与实施步骤

### 任务 0：代码审查历史修复 Git 提交基线对齐

**文件：**
- 涉及工作区：`D:\microservice`

- [ ] **步骤 1：检查工作区状态并提交存量 16 项代码审查修复**

```bash
git add deploy/ educloud-backend/ scratch/ docs/
git commit -m "fix: 修复代码审查报告 16 项安全与交易类 BUG（BUG-001~006/016~022/034~037/051~053）"
```

- [ ] **步骤 2：验证分支干净**

运行：`git status`
预期：`nothing to commit, working tree clean`

---

### 任务 1：Maven 父工程与 `educloud-payment` 模块脚手架

**文件：**
- 修改：`educloud-backend/pom.xml`
- 创建：`educloud-backend/educloud-payment/pom.xml`
- 创建：`educloud-backend/educloud-payment/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/PaymentApplication.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/PaymentApplicationTests.java`

- [ ] **步骤 1：在父 `pom.xml` 注册 `educloud-payment`**
- [ ] **步骤 2：编写 `educloud-payment/pom.xml`**
- [ ] **步骤 3：编写 `application.yml` 配置（业务端口 8093，监控端口 8094，数据库 `educloud_payment`）**
- [ ] **步骤 4：编写启动类 `PaymentApplication.java`**
- [ ] **步骤 5：运行 Maven 编译测试**

运行：`mvn test-compile -pl educloud-payment`
预期：`BUILD SUCCESS`

- [ ] **步骤 6：Commit**

```bash
git add educloud-backend/pom.xml educloud-backend/educloud-payment
git commit -m "feat(payment): 初始化 educloud-payment 模块脚手架与双端口配置"
```

---

### 任务 2：数据库迁移脚本与基础设施配置

**文件：**
- 创建：`deploy/sql/payment/V000__technical_tables.sql`
- 创建：`deploy/sql/payment/V001__init_payment_schema.sql`
- 创建：`deploy/sql/payment/V002__payment_seed_data.sql`
- 创建：`deploy/sql/user/V008__payment_permissions.sql`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/IdentifierConfig.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/HealthIndicatorConfig.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/MybatisPlusConfig.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/SecurityConfig.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/InternalApiFilter.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/PaymentProperties.java`

- [ ] **步骤 1：编写 7 张核心表 SQL 迁移脚本（含雪花主键、金额分、乐观锁、审计字段）**
- [ ] **步骤 2：编写 V008 权限迁移脚本（追加 `refund:admin`、`reconciliation:admin` 权限码）**
- [ ] **步骤 3：实现雪花 ID 发生器、深度健康检查、OAuth2 JWT 安全拦截与内部安全过滤器**
- [ ] **步骤 4：测试编译验证**

运行：`mvn test-compile -pl educloud-payment`
预期：`BUILD SUCCESS`

- [ ] **步骤 5：Commit**

```bash
git add deploy/sql/ educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/
git commit -m "feat(payment): 数据库迁移脚本、雪花ID发生器与基础设施安全配置"
```

---

### 任务 3：领域实体、枚举与 MyBatis-Plus Mappers

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/enums/`
  - `PaymentStatus.java`, `RefundStatus.java`, `PaymentChannel.java`, `TradeType.java`, `DiffType.java`, `ResolveAction.java`, `ResolveStatus.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/entity/`
  - `PaymentOrderEntity.java`, `PaymentTransactionEntity.java`, `PaymentCallbackLogEntity.java`, `PaymentRefundEntity.java`, `ReconciliationBatchEntity.java`, `ReconciliationDiffEntity.java`, `PaymentOutboxEventEntity.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/mapper/`
  - `PaymentOrderMapper.java`, `PaymentTransactionMapper.java`, `PaymentCallbackLogMapper.java`, `PaymentRefundMapper.java`, `ReconciliationBatchMapper.java`, `ReconciliationDiffMapper.java`, `PaymentOutboxEventMapper.java`

- [ ] **步骤 1：编写状态枚举（含状态流转合法性校验方法）**
- [ ] **步骤 2：编写数据实体类（标注 `@JsonSerialize(using = ToStringSerializer.class)`、`@Version`、`@TableLogic`）**
- [ ] **步骤 3：编写 Mapper 接口与带有 CAS 条件更新的自定义方法**
- [ ] **步骤 4：测试编译**

运行：`mvn test-compile -pl educloud-payment`
预期：`BUILD SUCCESS`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/enums/ educloud-backend/educloud-payment/src/main/java/com/educloud/payment/entity/ educloud-backend/educloud-payment/src/main/java/com/educloud/payment/mapper/
git commit -m "feat(payment): 领域实体、状态枚举与 MyBatis-Plus Mapper 数据访问层"
```

---

### 任务 4：统一错误码体系、全局异常与 OpenFeign 订单客户端

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/exception/PaymentErrorCode.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/exception/PaymentException.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/exception/GlobalExceptionHandler.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/feign/OrderClient.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/feign/dto/OrderPayableSnapshotResponse.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/exception/GlobalExceptionHandlerTest.java`

- [ ] **步骤 1：定义标准错误码体系（`PAYMENT_ORDER_NOT_FOUND`, `PAYMENT_EXPIRED`, `AMOUNT_MISMATCH`, `SIGN_VERIFY_FAILED`, `DUPLICATE_PAYMENT`, `REFUND_NOT_ALLOWED` 等）**
- [ ] **步骤 2：编写全局异常处理器，统一输出 API Result 信封**
- [ ] **步骤 3：编写 `OrderClient` Feign 客户端（调用 `educloud-order` 的 `/internal/v1/orders/{id}/payable-snapshot`）**
- [ ] **步骤 4：编写异常处理单元测试并验证**

运行：`mvn test -Dtest=GlobalExceptionHandlerTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 2, Failures: 0`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/exception/ educloud-backend/educloud-payment/src/main/java/com/educloud/payment/feign/
git commit -m "feat(payment): 错误码体系、全局异常拦截与 OrderClient 内部 RPC 契约"
```

---

### 任务 5：统一支付 SPI 契约与 Mock 沙箱渠道实现（TDD）

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/PaymentChannelPlugin.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/PaymentChannelFactory.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/model/`
  - `PaymentContext.java`, `UnifiedPayResult.java`, `CallbackVerifyResult.java`, `RefundContext.java`, `UnifiedRefundResult.java`, `UnifiedQueryResult.java`, `ChannelBillItem.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/plugins/MockPaymentPlugin.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/spi/MockPaymentPluginTest.java`

- [ ] **步骤 1：编写失败的 Mock 渠道单元测试**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：实现统一 SPI 接口契约与 `MockPaymentPlugin`**
  - 实现统一下单（生成模拟二维码与 URL）、回调验签（模拟合法性）、模拟退款（立即成功）、模拟账单生成（支持注入测试差错单）。
- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=MockPaymentPluginTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 4, Failures: 0`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/
git commit -m "feat(payment): 统一支付 SPI 契约抽象与全功能 Mock 沙箱插件实现"
```

---

### 任务 6：支付宝 EasySDK 与微信支付 V3 渠道插件实现

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/plugins/AlipayEasySdkPlugin.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/plugins/WeChatPayV3Plugin.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/spi/AlipayEasySdkPluginTest.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/spi/WeChatPayV3PluginTest.java`

- [ ] **步骤 1：编写支付宝与微信插件的单元测试（模拟 RSA2 与 V3 证书验签）**
- [ ] **步骤 2：实现 `AlipayEasySdkPlugin`（PC 网页支付、扫码支付、RSA2 验签、原路退款、账单下载）**
- [ ] **步骤 3：实现 `WeChatPayV3Plugin`（Native 扫码下单、平台公钥证书验签、AEAD_AES_256_GCM 解密、原路退款）**
- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=AlipayEasySdkPluginTest,WeChatPayV3PluginTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 4, Failures: 0`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/spi/plugins/
git commit -m "feat(payment): 支付宝 EasySDK 与微信支付 V3 渠道插件及公私钥安全验签"
```

---

### 任务 7：支付核心服务与 CAS 状态机流转（TDD）

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/PaymentService.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/impl/PaymentServiceImpl.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentCashierController.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/dto/request/CashierPayRequest.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/dto/response/CashierPayResponse.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/dto/response/PaymentDetailResponse.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/service/PaymentServiceTest.java`

- [ ] **步骤 1：编写失败的 `PaymentServiceTest` 单元测试（提单、状态机 CAS、失效拦截、Mock 确认）**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：实现 `PaymentServiceImpl` 与 `PaymentCashierController`**
  - 收银台发起支付：Feign 校验订单 `PENDING_PAYMENT`，创建 `payment_order`（`PAYING`），记录通信流水；
  - 查单轮询：返回支付单最新状态与支付凭证；
  - Mock 确认：CAS 更新为 `SUCCESS`，写入 Outbox 发件箱。
- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=PaymentServiceTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 5, Failures: 0`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/ educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/ educloud-backend/educloud-payment/src/main/java/com/educloud/payment/dto/
git commit -m "feat(payment): 支付收银台服务、CAS 状态机跃迁与主动轮询端点"
```

---

### 任务 8：异步回调安全验签与防重放控制

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentCallbackController.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/PaymentCallbackService.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/impl/PaymentCallbackServiceImpl.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/controller/PaymentCallbackControllerTest.java`

- [ ] **步骤 1：编写异步回调单元测试（验签失败拒绝、Redis 防重放、金额防篡改）**
- [ ] **步骤 2：实现 `PaymentCallbackController` 与 `PaymentCallbackServiceImpl`**
  - Redis 60s 分布式排他锁 + DB 幂等流水校验；
  - 调用 SPI `verifyAndParseCallback` 验签；
  - 本地短事务：校验金额 ➔ CAS 更新支付单为 `SUCCESS` ➔ 写入 Outbox ➔ 释放 Redis 锁；
  - 针对支付宝返回 `"success"`，针对微信返回 JSON `{"code":"SUCCESS","message":"OK"}`。
- [ ] **步骤 3：运行测试验证通过**

运行：`mvn test -Dtest=PaymentCallbackControllerTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 3, Failures: 0`

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentCallbackController.java educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/PaymentCallbackService.java educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/impl/PaymentCallbackServiceImpl.java
git commit -m "feat(payment): 异步回调公网接收入口、Redis 排他锁防重放与严格签名校验"
```

---

### 任务 9：Transactional Outbox 本地发件箱与 RabbitMQ 事件广播

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/RabbitPaymentConfig.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/messaging/OutboxEventWriter.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/messaging/PaymentOutboxRelay.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/messaging/events/PaymentSucceededEvent.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/messaging/events/PaymentRefundedEvent.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/messaging/PaymentOutboxRelayTest.java`

- [ ] **步骤 1：编写 RabbitMQ TopicExchange `educloud.payment.exchange` 配置**
- [ ] **步骤 2：实现 `OutboxEventWriter`（本地事务同库原子写入）与事件模型**
- [ ] **步骤 3：实现 `PaymentOutboxRelay`（定时抢占式认领 `PENDING ➔ SENDING`，投递成功置 `PUBLISHED`，失败指数退避）**
- [ ] **步骤 4：编写测试验证 Outbox 调度投递正常**

运行：`mvn test -Dtest=PaymentOutboxRelayTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 2, Failures: 0`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/messaging/ educloud-backend/educloud-payment/src/main/java/com/educloud/payment/config/RabbitPaymentConfig.java
git commit -m "feat(payment): Transactional Outbox 本地发件箱与 RabbitMQ 消息可靠广播"
```

---

### 任务 10：下游微服务消费集成（`educloud-order` 与 `educloud-course`）

**文件：**
- 修改：`educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/PaymentEventListener.java`
- 修改：`educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/RabbitOrderConfig.java`
- 修改：`educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/PaymentRefundListener.java`
- 修改：`educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/RabbitConfiguration.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/messaging/PaymentEventListenerTest.java`
- 测试：`educloud-backend/educloud-course/src/test/java/com/educloud/course/messaging/PaymentRefundListenerTest.java`

- [ ] **步骤 1：在 `educloud-order` 声明 `order.payment.success.queue` 与 `order.payment.refund.queue`**
- [ ] **步骤 2：在 `educloud-order` 编写 `PaymentEventListener`（消费 `PaymentSucceededEvent` CAS 置 `PAID` 并发 `OrderPaidEvent` 开课；消费 `PaymentRefundedEvent` CAS 置 `REFUNDED`）**
- [ ] **步骤 3：在 `educloud-course` 声明 `course.payment.refund.queue` 并编写 `PaymentRefundListener`（消费 `PaymentRefundedEvent` 将选课权限置为 `REVOKED`）**
- [ ] **步骤 4：运行测试验证消费逻辑与幂等性**

运行：`mvn test -Dtest=PaymentEventListenerTest,PaymentRefundListenerTest`
预期：`BUILD SUCCESS, Tests run: 4, Failures: 0`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-order/ educloud-backend/educloud-course/
git commit -m "feat(order,course): 订阅支付中心支付成功与退款事件，实现顺逆向履约闭环"
```

---

### 任务 11：退款管理与审核流服务

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/RefundService.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/impl/RefundServiceImpl.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentRefundController.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/dto/request/RefundApplyRequest.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/dto/request/RefundAuditRequest.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/service/RefundServiceTest.java`

- [ ] **步骤 1：编写失败的 `RefundServiceTest` 单元测试**
- [ ] **步骤 2：实现退款申请校验（必须为 PAID/FULFILLED 订单且退款金额不超过实付金额）**
- [ ] **步骤 3：实现财务审核与原路退款（通过即调 SPI `initiateRefund`，成功写入 Outbox 发送 `PaymentRefundedEvent`）**
- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=RefundServiceTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 3, Failures: 0`

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/RefundService.java educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/impl/RefundServiceImpl.java educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/PaymentRefundController.java
git commit -m "feat(payment): 退款申请、财务审核与渠道原路退款全流程"
```

---

### 任务 12：日终对账中心与 4 类差错平账引擎

**文件：**
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/ReconciliationEngine.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/impl/ReconciliationEngineImpl.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/ReconciliationController.java`
- 创建：`educloud-backend/educloud-payment/src/main/java/com/educloud/payment/dto/request/DiffResolveRequest.java`
- 测试：`educloud-backend/educloud-payment/src/test/java/com/educloud/payment/service/ReconciliationEngineTest.java`

- [ ] **步骤 1：编写双向比对与 4 类差错识别的单元测试**
- [ ] **步骤 2：实现 `ReconciliationEngineImpl`（拉取渠道账单与本地流水，全外连接核对，生成批次与差错单）**
- [ ] **步骤 3：实现差错平账处理动作（`MANUAL_REPAIR`, `REFUND_OFFLINE`, `ADJUST_AMOUNT`, `MANUAL_SYNC`, `IGNORE`）**
- [ ] **步骤 4：编写 `ReconciliationController` 对外暴露对账触发与差错管理端点**
- [ ] **步骤 5：运行测试验证通过**

运行：`mvn test -Dtest=ReconciliationEngineTest -pl educloud-payment`
预期：`BUILD SUCCESS, Tests run: 4, Failures: 0`

- [ ] **步骤 6：Commit**

```bash
git add educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/ReconciliationEngine.java educloud-backend/educloud-payment/src/main/java/com/educloud/payment/service/impl/ReconciliationEngineImpl.java educloud-backend/educloud-payment/src/main/java/com/educloud/payment/controller/ReconciliationController.java
git commit -m "feat(payment): 日终双向对账比对引擎与 4 类差错人工平账处理"
```

---

### 任务 13：Gateway 网关路由配置与一键启动脚本适配

**文件：**
- 修改：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 修改：`deploy/scripts/start-dev.sh`
- 测试：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/GatewayApplicationTests.java`

- [ ] **步骤 1：在网关 `application.yml` 追加 `educloud-payment` 路由配置（前缀 `/api/v1/payments/**`, `/api/v1/payment-callbacks/**`, `/api/v1/payment-refunds/**`, `/api/v1/reconciliations/**`）**
- [ ] **步骤 2：在 `start-dev.sh` 脚本追加 `educloud-payment` 启动块（注入 `EDUCLOUD_ENVIRONMENT=local`，健康端口 8094 探测）**
- [ ] **步骤 3：验证网关与启动脚本语法**

运行：`mvn test-compile -pl educloud-gateway`
预期：`BUILD SUCCESS`

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-gateway/src/main/resources/application.yml deploy/scripts/start-dev.sh
git commit -m "feat(gateway,deploy): 接入 payment 网关路由与 VM start-dev.sh 一键启动脚本"
```

---

### 任务 14：学生端收银台多渠道选择与轮询支付

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Checkout.tsx`
- 修改：`educloud-frontend/student-portal/src/pages/Orders.tsx`
- 修改：`educloud-frontend/student-portal/src/services/order.ts`
- 创建：`educloud-frontend/student-portal/src/services/payment.ts`

- [ ] **步骤 1：编写 `payment.ts` API 客户端封装（`createCashierPay`, `getPaymentStatus`, `mockConfirmPay`, `applyRefund`）**
- [ ] **步骤 2：升级 `Checkout.tsx` 收银台，支持选择 Mock/支付宝/微信，唤起二维码弹窗并启动 3 秒心跳轮询，支付成功直达学习页**
- [ ] **步骤 3：在 `Orders.tsx` 增加“申请退款”弹窗交互**
- [ ] **步骤 4：前端 TypeScript 类型检查与打包验证**

运行：`cd educloud-frontend/student-portal && npm run build`
预期：`vite build` 成功，0 错误。

- [ ] **步骤 5：Commit**

```bash
git add educloud-frontend/student-portal/
git commit -m "feat(student): 收银台接入多渠道支付、动态二维码弹窗与订单申请退款"
```

---

### 任务 15：管理端退款管理与对账中心页面

**文件：**
- 创建：`educloud-frontend/admin-portal/src/pages/RefundManage.tsx`
- 创建：`educloud-frontend/admin-portal/src/pages/ReconciliationManage.tsx`
- 创建：`educloud-frontend/admin-portal/src/services/paymentAdmin.ts`
- 修改：`educloud-frontend/admin-portal/src/App.tsx`
- 修改：`educloud-frontend/admin-portal/src/components/layout/AdminLayout.tsx`

- [ ] **步骤 1：编写 `paymentAdmin.ts` 封装退款列表/审批与对账批次/差错平账接口**
- [ ] **步骤 2：开发 `RefundManage.tsx` 退款管理页面（多维列表、审批通过/驳回弹窗）**
- [ ] **步骤 3：开发 `ReconciliationManage.tsx` 对账中心页面（顶部概览看板、一键对账、4 类差错列表与平账弹窗）**
- [ ] **步骤 4：在管理端侧边栏与路由注册两页面**
- [ ] **步骤 5：前端构建验证**

运行：`cd educloud-frontend/admin-portal && npm run build`
预期：`vite build` 成功，0 错误。

- [ ] **步骤 6：Commit**

```bash
git add educloud-frontend/admin-portal/
git commit -m "feat(admin): 新增管理端退款审核中心与日终对账平账管理页面"
```

---

### 任务 16：E2E 全链路集成测试与 Playwright 自动化验收

**文件：**
- 创建：`scratch/test_payment_e2e.py`
- 创建：`scratch/test_payment_playwright.py`

- [ ] **步骤 1：编写 `scratch/test_payment_e2e.py` 自动化覆盖 9 大业务阶段**
  - 1. 学员登录 ➔ 课程加购 ➔ 提交订单；
  - 2. 请求支付中心 `/api/v1/payments/cashier` 创建 Mock 支付单；
  - 3. 轮询并确认 Mock 支付；
  - 4. 验证 Outbox 广播 ➔ 订单状态自动跃迁为 `PAID`；
  - 5. 验证课程中心自动开课；
  - 6. 学员发起退款申请 ➔ 管理员登录审核通过并原路退款；
  - 7. 验证订单状态跃迁为 `REFUNDED` 且课程权限状态变更为 `REVOKED`；
  - 8. 手动触发日终对账批次；
  - 9. 检索差错单并执行人工平账处置。
- [ ] **步骤 2：编写 `scratch/test_payment_playwright.py` 执行浏览器全链路实测并保存截图到 `scratch/screenshots/payment/`**
- [ ] **步骤 3：运行本地与 VM 部署，执行全量自动化验证，确认 100% 通过**
- [ ] **步骤 4：Commit 验收脚本与测试结果**

```bash
git add scratch/
git commit -m "test(payment): M08 支付中心与收银台 E2E 全链路测试与 Playwright 浏览器验收"
```

---

## 质量门禁检查清单

- [ ] **后端全量编译**：`mvn clean test` 全量通过，BUILD SUCCESS。
- [ ] **前端全量构建**：`student-portal`、`teacher-portal`、`admin-portal` 执行 `tsc && vite build` 0 报错。
- [ ] **雪花 ID 规范**：全量 ID 字段返回前端为 String，禁止 Number 化。
- [ ] **API Result 信封**：全量端点返回统一 Result 信封。
- [ ] **JDBC 字符集**：MySQL 连接串使用 `characterEncoding=utf8`。
- [ ] **Spring Cloud LoadBalancer**：显式引入依赖。
- [ ] **E2E 自动化测试**：`test_payment_e2e.py` 9 大阶段 100% PASS。
- [ ] **Playwright UI 自动化**：真实浏览器测试与截图留存。
