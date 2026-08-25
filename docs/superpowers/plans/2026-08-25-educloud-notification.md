# EduCloud M10 消息通知中心（educloud-notification）实施计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建 EduCloud M10 消息通知中心微服务（`educloud-notification`），实现站内信收件箱、个人已读状态隔离、未读小红点统计、邮件/短信 SPI 插件架构、RabbitMQ 领域事件自动消费与异步投递重试引擎。

**架构：** 基于 Spring Boot 3.2.5 + MyBatis-Plus + OpenFeign + RabbitMQ + Redis，通过统一网关（8080）对外暴露 REST API（8097 业务 / 8098 监控），独立 MySQL 逻辑库 `educloud_notification`，支持多端收件箱查询与全链路异步通知。

**技术栈：** Java 17、Spring Boot 3.2.5、Spring Cloud Alibaba Nacos 2023.0.1.0、MyBatis-Plus 3.5.5、JJWT 0.12.5、RabbitMQ 3.13、Redis 7.2、MySQL 8.0.36、JUnit 5。

---

## 任务列表

### 任务 1：创建数据库迁移脚本与 Maven 工程配置

**文件：**
- 创建：`deploy/sql/notification/V000__technical_tables.sql`
- 创建：`deploy/sql/notification/V001__notifications.sql`
- 创建：`deploy/sql/user/V010__notification_permissions.sql`
- 修改：`educloud-backend/educloud-notification/pom.xml`
- 修改：`educloud-backend/educloud-notification/src/main/resources/application.yml`

- [ ] **步骤 1：编写数据库初始化脚本**
  - `V000__technical_tables.sql`：创建技术库基础表；
  - `V001__notifications.sql`：创建 `sys_notification`、`sys_user_notification`、`sys_delivery_task`；
  - `V010__notification_permissions.sql`：插入权限码 151 (`notification:publish`)、152 (`notification:channel:view`)、153 (`notification:channel:test`) 并关联 `ROLE_ADMIN`。

- [ ] **步骤 2：完善 Maven pom.xml 依赖**
  - 引入 `educloud-common`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-oauth2-resource-server`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、`spring-boot-starter-data-redis`、`spring-boot-starter-amqp`、`spring-cloud-starter-openfeign`、`spring-cloud-starter-alibaba-nacos-discovery` 等。

- [ ] **步骤 3：编写 application.yml**
  - 配置端口 8097（业务）/ 8098（监控）、Nacos、MySQL、Redis、RabbitMQ 连接及日志配置。

- [ ] **步骤 4：运行构建验证**
  - 运行 `mvn -f educloud-backend/educloud-notification/pom.xml compile`
  - 预期：BUILD SUCCESS。

- [ ] **步骤 5：Commit**
  - `git add deploy/sql/notification deploy/sql/user educloud-backend/educloud-notification/pom.xml educloud-backend/educloud-notification/src/main/resources/application.yml`
  - `git commit -m "feat(notification): add sql migrations and project configuration"`

---

### 任务 2：数据实体、枚举与 MyBatis-Plus Mapper

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/enums/NotificationKind.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/enums/TargetType.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/enums/DeliveryStatus.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/enums/ChannelCode.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/entity/NotificationEntity.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/entity/UserNotificationEntity.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/entity/DeliveryTaskEntity.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/mapper/NotificationMapper.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/mapper/UserNotificationMapper.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/mapper/DeliveryTaskMapper.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/mapper/NotificationMapperTest.java`

- [ ] **步骤 1：编写 Mapper 单元测试**
- [ ] **步骤 2：实现实体类与枚举**
- [ ] **步骤 3：实现 Mapper 接口**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

---

### 任务 3：邮件渠道 SPI 抽象与插件体系

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/spi/model/EmailSendContext.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/spi/model/EmailSendResult.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/spi/EmailChannelPlugin.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/spi/plugins/MockEmailPlugin.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/spi/plugins/SmtpEmailPlugin.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/spi/EmailChannelFactory.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/spi/EmailChannelPluginTest.java`

- [ ] **步骤 1：编写 SPI 插件测试**
- [ ] **步骤 2：实现 MockEmailPlugin 与 SmtpEmailPlugin**
- [ ] **步骤 3：实现 EmailChannelFactory**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

---

### 任务 4：通知收件箱核心服务与单元测试

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/dto/request/PublishNotificationRequest.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/dto/response/NotificationResponse.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/dto/response/UnreadCountResponse.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/service/NotificationService.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/service/impl/NotificationServiceImpl.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/service/NotificationServiceTest.java`

- [ ] **步骤 1：编写 NotificationService 单元测试**（测试定向发送、全员公告、分页查询、未读数统计、标记已读归属越权防护、一键全部已读、逻辑删除）
- [ ] **步骤 2：实现 DTO 与响应模型**
- [ ] **步骤 3：实现 NotificationServiceImpl**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

---

### 任务 5：邮件渠道管理服务与安全自测端点

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/dto/response/EmailChannelStatusResponse.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/dto/request/EmailTestSendRequest.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/service/EmailChannelService.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/service/impl/EmailChannelServiceImpl.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/controller/EmailChannelStatusController.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/controller/EmailChannelStatusControllerTest.java`

- [ ] **步骤 1：编写 EmailChannelStatusController 测试**（测试 SMTP 密码严格脱敏、限频发信 Redis 60s、发信审计）
- [ ] **步骤 2：实现脱敏逻辑与安全限频**
- [ ] **步骤 3：实现 EmailChannelStatusController**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

---

### 任务 6：用户收件箱与管理端发布 REST 控制器

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/controller/NotificationController.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/controller/AdminNotificationController.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/controller/NotificationControllerTest.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/controller/AdminNotificationControllerTest.java`

- [ ] **步骤 1：编写 Controller MockMvc 单元测试**
- [ ] **步骤 2：实现 NotificationController 与 AdminNotificationController**
- [ ] **步骤 3：运行测试验证通过**
- [ ] **步骤 4：Commit**

---

### 任务 7：RabbitMQ 跨微服务领域事件消费者

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/config/RabbitMqConfiguration.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/events/PaymentSucceededEvent.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/events/OrderRefundedEvent.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/events/LiveStartedEvent.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/events/AssignmentGradedEvent.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/DomainNotificationConsumer.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/messaging/DomainNotificationConsumerTest.java`

- [ ] **步骤 1：编写 DomainNotificationConsumer 消费防重测试**
- [ ] **步骤 2：实现 RabbitMQ 队列/交换机绑定配置**
- [ ] **步骤 3：实现事件监听与入库派发**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

---

### 任务 8：外部渠道异步投递重试引擎

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/support/DeliveryTaskJob.java`
- 测试：`educloud-backend/educloud-notification/src/test/java/com/educloud/notification/support/DeliveryTaskJobTest.java`

- [ ] **步骤 1：编写 DeliveryTaskJob 指数退避与失败审计测试**
- [ ] **步骤 2：实现 DeliveryTaskJob 定时扫描与重试执行器**
- [ ] **步骤 3：运行测试验证通过**
- [ ] **步骤 4：Commit**

---

### 任务 9：安全配置、内部互信与网关集成

**文件：**
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/security/InternalApiFilter.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/security/JwksLoader.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/security/NotificationJwtValidator.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/config/SecurityConfiguration.java`
- 修改：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 修改：`deploy/scripts/start-dev.sh`

- [ ] **步骤 1：实现 SecurityConfiguration 与 Jwks 验签**
- [ ] **步骤 2：支持 `X-Internal-Token` 内部互信**
- [ ] **步骤 3：在 `start-dev.sh` 脚本中注册 `educloud-notification`（8097/8098）**
- [ ] **步骤 4：运行全模块本地测试验证**
- [ ] **步骤 5：Commit**

---

### 任务 10：虚拟机同步、全量编译部署与全链路自动化验证

**文件：**
- 创建：`scratch/test_notification_e2e.py`
- 修改：`scratch/sync_files_to_vm.py`

- [ ] **步骤 1：编写 E2E 自动化测试脚本**（测试登录、未读统计、发布通知、标记已读、一键已读、脱敏自测发信、MQ 事件入库）
- [ ] **步骤 2：同步全量文件至虚拟机并初始化数据库 `educloud_notification`**
- [ ] **步骤 3：在虚拟机内执行 `mvn clean package -DskipTests=true`（验证全量 11 个模块编译通过）**
- [ ] **步骤 4：拉起服务并通过 Playwright/Chrome DevTools 自动化验证前端三端通知交互与控制台日志**
- [ ] **步骤 5：总结交付与输出 walkthrough.md**
