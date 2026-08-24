# M07 educloud-order（订单中心）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建并交付 EduCloud 订单中心微服务 `educloud-order`（端口 8091/8092），完成购物车管理、防重提单、权威课程价格快照固化、订单状态机流转、RabbitMQ TTL+DLX 15 分钟超时自动关单、支付模拟与 `OrderPaid` 广播联动 `educloud-course` 自动开课履约，并实现学生端与管理端前端三端闭环。

**架构：** 基于 Spring Boot 3.2.5 + MyBatis-Plus + Nacos + Redis + RabbitMQ 双端口微服务架构；业务数据存储在独立逻辑库 `educloud_order`；利用 Redis Token 实现分布式防重；利用 RabbitMQ 死信交换机实现精准延时关单；通过事务 Outbox + RabbitMQ 广播领域事件驱动下游履约。

**技术栈：** Java 17, Spring Boot 3.2.5, Spring Cloud Alibaba (Nacos), MyBatis-Plus 3.5.12, MySQL 8.0, Redis, RabbitMQ (AMQP), OpenFeign, React 18, Tailwind CSS, Vite.

---

## 文件结构与模块规划

```
educloud-backend/
├── pom.xml                                            # 注册 educloud-order 模块
└── educloud-order/
    ├── pom.xml                                        # 模块依赖管理
    └── src/
        ├── main/
        │   ├── java/com/educloud/order/
        │   │   ├── OrderApplication.java              # 启动类
        │   │   ├── config/
        │   │   │   ├── IdentifierConfig.java          # 雪花 ID 发生器
        │   │   │   ├── HealthIndicatorConfig.java     # 健康探测
        │   │   │   ├── MybatisPlusConfig.java         # MyBatis-Plus 分页与乐观锁
        │   │   │   ├── RabbitOrderConfig.java         # TTL + DLX 死信队列配置
        │   │   │   ├── RedisConfig.java               # Redis 序列化与 Lua 脚本
        │   │   │   ├── SecurityConfig.java            # 安全与鉴权
        │   │   │   └── InternalApiFilter.java         # 内部 RPC Header 校验
        │   │   ├── controller/
        │   │   │   ├── CartController.java            # 购物车端点
        │   │   │   ├── OrderStudentController.java    # 学生提单/查单/取消/模拟支付
        │   │   │   ├── OrderAdminController.java      # 管理端订单与退款端点
        │   │   │   └── InternalOrderController.java   # 内部快照查询 RPC
        │   │   ├── dto/
        │   │   │   ├── request/                       # 提单/购物车请求 DTO
        │   │   │   └── response/                      # 响应包装与快照 DTO
        │   │   ├── entity/                            # MyBatis-Plus 数据实体
        │   │   ├── exception/                         # 错误码与全局异常处理
        │   │   ├── feign/                             # Course 服务远程调用客户端
        │   │   ├── mapper/                            # 数据访问 Mapper
        │   │   ├── messaging/                         # 延时关单与 OrderPaid 消息收发
        │   │   └── service/                           # 购物车与订单状态机服务实现
        │   └── resources/
        │       └── application.yml                    # 端口 8091/8092, Nacos, MySQL, Redis, RabbitMQ 配置
        └── test/                                      # 单元与切片测试

deploy/sql/order/
├── V000__technical_tables.sql                         # Outbox 事务日志与技术表
├── V001__init_order_schema.sql                        # 购物车与订单主子表
└── V002__order_seed_data.sql                          # 初始测试订单种子数据
```

---

## 任务拆解与实施步骤

### 任务 1：Maven 父工程与 `educloud-order` 模块脚手架

**文件：**
- 修改：`educloud-backend/pom.xml:15-23`
- 创建：`educloud-backend/educloud-order/pom.xml`
- 创建：`educloud-backend/educloud-order/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/OrderApplication.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/OrderApplicationTests.java`

- [ ] **步骤 1：在父 pom.xml 中添加 `educloud-order` 模块**

在 `educloud-backend/pom.xml` 的 `<modules>` 标签下追加 `<module>educloud-order</module>`。

- [ ] **步骤 2：创建 `educloud-order/pom.xml`**

引入 `educloud-common`、`spring-boot-starter-web`、`spring-boot-starter-security`、`spring-boot-starter-data-redis`、`spring-boot-starter-amqp`、`spring-cloud-starter-alibaba-nacos-discovery`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、`spring-cloud-starter-openfeign` 等依赖。

- [ ] **步骤 3：编写 `application.yml` 配置文件**

配置端口：`server.port: 8091`，`management.server.port: 8092`；配置 Nacos、MySQL（数据库 `educloud_order`）、Redis、RabbitMQ 连接信息。

- [ ] **步骤 4：编写启动类 `OrderApplication.java` 与测试类**

```java
package com.educloud.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.educloud.order.mapper")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

- [ ] **步骤 5：运行 Maven 编译测试验证脚手架正常**

运行：`mvn test-compile -pl educloud-order`
预期：BUILD SUCCESS

- [ ] **步骤 6：Commit**

```bash
git add educloud-backend/pom.xml educloud-backend/educloud-order
git commit -m "feat(order): 初始化 educloud-order 微服务工程脚手架与依赖配置"
```

---

### 任务 2：数据库迁移脚本与基础设施配置

**文件：**
- 创建：`deploy/sql/order/V000__technical_tables.sql`
- 创建：`deploy/sql/order/V001__init_order_schema.sql`
- 创建：`deploy/sql/order/V002__order_seed_data.sql`
- 创建：`deploy/sql/user/V007__order_permissions.sql`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/config/IdentifierConfig.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/config/HealthIndicatorConfig.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/config/MybatisPlusConfig.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/config/SecurityConfig.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/config/InternalApiFilter.java`

- [ ] **步骤 1：编写 SQL 迁移脚本**

包含 `cart_item`、`trade_order`、`trade_order_item`、`refund_request`、`refund_request_item` 及 `outbox_event`。

- [ ] **步骤 2：实现 Snowflake `IdentifierConfig.java` 与 `HealthIndicatorConfig.java`**

配置 MyBatis-Plus 雪花 ID 生成器，保证主键全局唯一；实现多中间件聚合健康探测。

- [ ] **步骤 3：实现 `SecurityConfig.java` 与 `InternalApiFilter.java`**

配置 OAuth2 Resource Server JWT 解码器与权限拦截规则；内部 `/internal/v1/**` 端点强制校验安全密钥 Header。

- [ ] **步骤 4：运行测试确认配置可加载**

运行：`mvn test-compile -pl educloud-order`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add deploy/sql educloud-backend/educloud-order/src/main/java/com/educloud/order/config
git commit -m "feat(order): 添加数据库迁移脚本与基础设施安全/主键/健康探测配置"
```

---

### 任务 3：领域实体、枚举与 MyBatis-Plus Mappers

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/OrderStatus.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/FulfillmentStatus.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/CartItemEntity.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/TradeOrderEntity.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/TradeOrderItemEntity.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/RefundRequestEntity.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/RefundRequestItemEntity.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/entity/OutboxEventEntity.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/CartItemMapper.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/TradeOrderMapper.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/TradeOrderItemMapper.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/RefundRequestMapper.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/RefundRequestItemMapper.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/OutboxEventMapper.java`

- [ ] **步骤 1：编写枚举与实体类**

严格定义雪花主键 `@TableId(type = IdType.ASSIGN_ID)`，`TradeOrderEntity` 包含 `@Version private Integer version;`。

- [ ] **步骤 2：编写 Mapper 接口与自定义 CAS 更新方法**

`TradeOrderMapper` 包含 `int updateStatusWithCas(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);`。

- [ ] **步骤 3：编译并验证 Mapper 绑定**

运行：`mvn test-compile -pl educloud-order`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/entity educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper
git commit -m "feat(order): 定义订单/购物车/退款核心实体与 MyBatis-Plus 数据访问层"
```

---

### 任务 4：错误码体系与异常拦截

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/exception/OrderErrorCode.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/exception/OrderBizException.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/exception/OrderExceptionHandler.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/exception/OrderExceptionHandlerTest.java`

- [ ] **步骤 1：编写失败测试**

测试业务异常抛出时，`OrderExceptionHandler` 正确将其转化为 `{ code: 400xxx, message: "...", data: null }`。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn test -Dtest=OrderExceptionHandlerTest -pl educloud-order`
预期：FAIL

- [ ] **步骤 3：编写 `OrderErrorCode`、`OrderBizException` 与 `@RestControllerAdvice OrderExceptionHandler`**

覆盖：`CART_ITEM_NOT_FOUND`、`COURSE_NOT_ON_SALE`、`DUPLICATE_ORDER_SUBMISSION`、`ORDER_NOT_FOUND`、`ORDER_STATUS_INVALID`、`ORDER_EXPIRED`、`REFUND_AMOUNT_EXCEEDED` 等错误码。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=OrderExceptionHandlerTest -pl educloud-order`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/exception educloud-backend/educloud-order/src/test/java/com/educloud/order/exception
git commit -m "feat(order): 建立订单中心业务错误码体系与统一异常处理器"
```

---

### 任务 5：购物车管理模块（Cart CRUD 与状态校验）

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/request/CartAddRequest.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/request/CartSelectionRequest.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/response/CartItemResponse.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/response/CartSummaryResponse.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/service/CartService.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/service/impl/CartServiceImpl.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/CartController.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/service/CartServiceTest.java`

- [ ] **步骤 1：编写购物车业务失败测试**

测试添加购物车、重复添加幂等更新、批量查询、切换勾选、移除购物车。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn test -Dtest=CartServiceTest -pl educloud-order`
预期：FAIL

- [ ] **步骤 3：实现 `CartServiceImpl` 与 `CartController`**

实现加购、勾选切换、列表聚合与批量清理。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=CartServiceTest -pl educloud-order`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/dto educloud-backend/educloud-order/src/main/java/com/educloud/order/service educloud-backend/educloud-order/src/main/java/com/educloud/order/controller educloud-backend/educloud-order/src/test/java/com/educloud/order/service/CartServiceTest.java
git commit -m "feat(order): 实现购物车加购、勾选、删除与汇总查询功能"
```

---

### 任务 6：下单防重 Token 机制与 Redis 校验

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/service/IdempotencyService.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/service/impl/IdempotencyServiceImpl.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/IdempotencyController.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/service/IdempotencyServiceTest.java`

- [ ] **步骤 1：编写防重 Token 测试**

测试生成 Token、第一次校验成功且删除、第二次相同 Token 校验失败抛出 `DUPLICATE_ORDER_SUBMISSION`。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn test -Dtest=IdempotencyServiceTest -pl educloud-order`
预期：FAIL

- [ ] **步骤 3：编写 Redis Lua 原子校验实现 `IdempotencyServiceImpl`**

```java
String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=IdempotencyServiceTest -pl educloud-order`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/service educloud-backend/educloud-order/src/main/java/com/educloud/order/controller educloud-backend/educloud-order/src/test/java/com/educloud/order/service/IdempotencyServiceTest.java
git commit -m "feat(order): 实现基于 Redis Lua 脚本的防重提单 Token 机制"
```

---

### 任务 7：Feign 远程课程查价与销售快照固化

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/feign/CourseClient.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/feign/dto/CourseSalesSnapshotDto.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/feign/CourseClientTest.java`

- [ ] **步骤 1：编写 Feign Client 接口与 DTO**

调用 `educloud-course` 服务的 `/api/v1/courses/{courseId}` 端点获取课程状态、标题、封面与最新售价。

- [ ] **步骤 2：编写单元测试模拟远程课程响应并校验未上架课程拦截**

运行：`mvn test -Dtest=CourseClientTest -pl educloud-order`
预期：PASS

- [ ] **步骤 3：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/feign educloud-backend/educloud-order/src/test/java/com/educloud/order/feign
git commit -m "feat(order): 添加 CourseClient 远程调用契约与课程销售快照获取"
```

---

### 任务 8：交易订单核心服务与状态机（下单、明细固化、主动取消）

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/request/OrderCreateRequest.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/response/OrderDetailResponse.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/response/OrderItemResponse.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/service/OrderService.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/service/impl/OrderServiceImpl.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/OrderStudentController.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/service/OrderServiceTest.java`

- [ ] **步骤 1：编写订单提单与主动取消业务测试**

测试单课快速下单、购物车合并下单、明细行金额与快照计算、学生主动取消待支付订单、已支付订单拦截取消。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn test -Dtest=OrderServiceTest -pl educloud-order`
预期：FAIL

- [ ] **步骤 3：实现 `OrderServiceImpl`**

在单事务内完成：生成雪花订单 ID 与流水号 `orderNo`、固化快照插入 `trade_order` 与 `trade_order_item`、清除购物车已结算项、设置 15 分钟失效时间 `expires_at`。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=OrderServiceTest -pl educloud-order`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/dto educloud-backend/educloud-order/src/main/java/com/educloud/order/service educloud-backend/educloud-order/src/main/java/com/educloud/order/controller educloud-backend/educloud-order/src/test/java/com/educloud/order/service/OrderServiceTest.java
git commit -m "feat(order): 实现交易订单创建、快照固化、详情查询与学生主动取消"
```

---

### 任务 9：RabbitMQ TTL + 死信（DLX）超时关单队列

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/config/RabbitOrderConfig.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/OrderDelayProducer.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/OrderDelayConsumer.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/messaging/OrderDelayCancelTest.java`

- [ ] **步骤 1：编写死信超时关单单元测试**

测试当订单仍为 `PENDING_PAYMENT` 时，死信消费者 CAS 执行状态变更为 `CANCELLED` 并写入取消时间；当订单已为 `PAID` 时，消费者静默跳过。

- [ ] **步骤 2：编写 `RabbitOrderConfig`**

声明普通延时队列 `educloud.order.delay.queue`（绑定死信交换机 `educloud.order.dlx.exchange`）与关单处理队列 `educloud.order.cancel.queue`。

- [ ] **步骤 3：编写生产者与消费者**

在提单事务提交后发送延时消息；消费者执行 CAS 关单。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=OrderDelayCancelTest -pl educloud-order`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/config/RabbitOrderConfig.java educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging educloud-backend/educloud-order/src/test/java/com/educloud/order/messaging
git commit -m "feat(order): 配置 RabbitMQ TTL+DLX 延时关单队列与幂等消费者"
```

---

### 任务 10：支付成功模拟、`OrderPaid` 事件广播与选课履约联动

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/OrderEventPublisher.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/PaymentEventListener.java`
- 修改：`educloud-backend/educloud-order/src/main/java/com/educloud/order/service/OrderService.java`
- 修改：`educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/OrderStudentController.java`
- 修改：`educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/OrderPaidListener.java`（新增监听器自动选课）
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/service/OrderMockPayTest.java`

- [ ] **步骤 1：编写支付成功流转测试**

测试调用 `mockPay` 将订单推进至 `PAID`，并向 Outbox 写入 `OrderPaid` 领域事件。

- [ ] **步骤 2：实现 `OrderStudentController.mockPay` 接口**

更新订单状态为 `PAID`，记录 `paid_at`，发布 `OrderPaid` 广播。

- [ ] **步骤 3：在 `educloud-course` 中实现 `OrderPaidListener`**

消费 `OrderPaid` 消息，提取 `studentId` 与 `courseIds`，幂等调用 `EnrollmentService` 为学生自动开课。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -Dtest=OrderMockPayTest -pl educloud-order`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-order educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging
git commit -m "feat(order): 实现 mock-pay 模拟支付、OrderPaid 广播与 Course 自动选课联动"
```

---

### 任务 11：内部微服务契约与管理端订单查询

**文件：**
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/response/OrderPayableSnapshotResponse.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/response/OrderFulfillmentSnapshotResponse.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/InternalOrderController.java`
- 创建：`educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/OrderAdminController.java`
- 测试：`educloud-backend/educloud-order/src/test/java/com/educloud/order/controller/InternalOrderControllerTest.java`

- [ ] **步骤 1：编写内部快照接口测试**

测试 `/internal/v1/orders/{id}/payable-snapshot` 与 `/internal/v1/orders/{id}/fulfillment-snapshot`。

- [ ] **步骤 2：实现 `InternalOrderController` 与 `OrderAdminController`**

提供管理端全量分页订单列表与内部快照服务。

- [ ] **步骤 3：运行测试验证通过**

运行：`mvn test -Dtest=InternalOrderControllerTest -pl educloud-order`
预期：PASS

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-order/src/main/java/com/educloud/order/controller educloud-backend/educloud-order/src/main/java/com/educloud/order/dto/response educloud-backend/educloud-order/src/test/java/com/educloud/order/controller
git commit -m "feat(order): 提供内部微服务快照 RPC 接口与管理端全量订单查询端点"
```

---

### 任务 12：网关与一键启动脚本适配

**文件：**
- 修改：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/AccessPolicy.java`
- 修改：`deploy/scripts/start-dev.sh`

- [ ] **步骤 1：核实网关路由规则**

确保 `/api/v1/cart/**`、`/api/v1/orders/**`、`/api/v1/refund-requests/**` 正确鉴权放行。

- [ ] **步骤 2：更新 `start-dev.sh`**

在启动脚本中加入 `educloud-order`（端口 8091/8092）的自动化编译、健康探测与守护进程启动逻辑。

- [ ] **步骤 3：Commit**

```bash
git add educloud-backend/educloud-gateway deploy/scripts/start-dev.sh
git commit -m "feat(deploy): 更新网关路由策略与 start-dev.sh 包含 educloud-order"
```

---

### 任务 13：学生端（student-portal）前端联调

**文件：**
- 修改：`educloud-frontend/student-portal/src/services/api.ts`
- 修改：`educloud-frontend/student-portal/src/pages/CourseDetail.tsx`
- 修改：`educloud-frontend/student-portal/src/pages/Checkout.tsx`
- 修改：`educloud-frontend/student-portal/src/pages/Orders.tsx`

- [ ] **步骤 1：在 `api.ts` 中封装购物车与订单真实 API**

`getCart`, `addToCart`, `getIdempotencyToken`, `createOrder`, `getMyOrders`, `getOrderDetail`, `cancelOrder`, `mockPayOrder`。

- [ ] **步骤 2：对接课程详情页与结算页**

点击“立即购买”获取防重 Token 提交订单，进入结算弹窗并支持点击“模拟支付”。

- [ ] **步骤 3：对接我的订单页（`/orders`）**

展示真实订单列表、金额、创建时间、状态徽标，支持“立即支付”、“取消订单”和“开始学习”。

- [ ] **步骤 4：构建验证**

运行：`cd educloud-frontend/student-portal && pnpm run build`
预期：0 报错输出 dist

- [ ] **步骤 5：Commit**

```bash
git add educloud-frontend/student-portal
git commit -m "feat(student): 学生端对接真实订单提单、模拟支付、订单列表与状态联动"
```

---

### 任务 14：管理端（admin-portal）前端联调

**文件：**
- 修改：`educloud-frontend/admin-portal/src/services/api.ts`
- 修改：`educloud-frontend/admin-portal/src/pages/OrderManage.tsx`

- [ ] **步骤 1：在管理端 `api.ts` 中封装全量订单查询接口**

- [ ] **步骤 2：在 `OrderManage.tsx` 中对接真实订单列表**

展示订单号、下单用户、购买课程、金额、支付时间、状态徽标（保持单行不折行保护）。

- [ ] **步骤 3：构建验证**

运行：`cd educloud-frontend/admin-portal && pnpm run build`
预期：0 报错输出 dist

- [ ] **步骤 4：Commit**

```bash
git add educloud-frontend/admin-portal
git commit -m "feat(admin): 管理端对接真实订单管理列表与订单详情查看"
```

---

### 任务 15：全链路集成与端到端自动化验收

**文件：**
- 创建：`scratch/test_order_e2e.py`
- 验证全量后端 `mvn clean test-compile`
- 验证 VM 虚拟机部署与三端运行状态

- [ ] **步骤 1：本地执行全量 Maven 编译**

运行：`mvn clean test-compile`
预期：全部 8 个模块（common, gateway, user, file, course, content, order 等）BUILD SUCCESS。

- [ ] **步骤 2：运行 Python 端到端自动化验收脚本**

覆盖：
1. 学员登录获取 Token
2. 加购课程并获取防重 Token
3. 提交订单生成 `trade_order`（状态 `PENDING_PAYMENT`）
4. 调用 `mock-pay` 支付成功（状态流转为 `PAID`）
5. 验证 `educloud-course` 自动生成 `course_enrollment` 选课记录
6. 验证学员可立即无缝访问该课程视频播放权限

- [ ] **步骤 3：提交交付文档与最终 Commit**

```bash
git add .
git commit -m "docs: 整理并提交 M07 订单中心完整实现与验收交付记录"
```
