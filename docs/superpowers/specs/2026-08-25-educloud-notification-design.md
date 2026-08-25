# EduCloud M10 消息通知中心（educloud-notification）技术设计规格

## 一、概述与定位

`educloud-notification` 是 EduCloud 平台的统一消息与通知触达中心，提供高吞吐的个人站内信收件箱、多端未读红点统计、跨微服务领域事件自动消费派发、邮件/短信通道 SPI 抽象以及高可用的异步投递重试引擎。

- **所属模块**：M10 消息通知中心（`educloud-notification`）
- **业务端口**：`8097`（业务 API）
- **监控端口**：`8098`（Actuator 监控）
- **逻辑数据库**：`educloud_notification`
- **网关路由**：`/api/v1/notifications/**`、`/api/v1/notification-channels/**` -> `lb://educloud-notification`

---

## 二、架构与模块划分

```
educloud-backend/educloud-notification/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/educloud/notification/
    │   │   ├── NotificationApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfiguration.java
    │   │   │   ├── NotificationProperties.java
    │   │   │   ├── RabbitMqConfiguration.java
    │   │   │   └── RedisConfiguration.java
    │   │   ├── controller/
    │   │   │   ├── NotificationController.java
    │   │   │   ├── AdminNotificationController.java
    │   │   │   └── EmailChannelStatusController.java
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   ├── PublishNotificationRequest.java
    │   │   │   │   ├── EmailTestSendRequest.java
    │   │   │   │   └── InternalSendNotificationRequest.java
    │   │   │   └── response/
    │   │   │       ├── NotificationResponse.java
    │   │   │       ├── UnreadCountResponse.java
    │   │   │       └── EmailChannelStatusResponse.java
    │   │   ├── entity/
    │   │   │   ├── NotificationEntity.java
    │   │   │   ├── UserNotificationEntity.java
    │   │   │   └── DeliveryTaskEntity.java
    │   │   ├── enums/
    │   │   │   ├── NotificationKind.java
    │   │   │   ├── TargetType.java
    │   │   │   ├── DeliveryStatus.java
    │   │   │   └── ChannelCode.java
    │   │   ├── exception/
    │   │   │   ├── NotificationErrorCode.java
    │   │   │   └── NotificationBizException.java
    │   │   ├── mapper/
    │   │   │   ├── NotificationMapper.java
    │   │   │   ├── UserNotificationMapper.java
    │   │   │   └── DeliveryTaskMapper.java
    │   │   ├── messaging/
    │   │   │   ├── DomainNotificationConsumer.java
    │   │   │   └── events/
    │   │   │       ├── PaymentSucceededEvent.java
    │   │   │       ├── OrderRefundedEvent.java
    │   │   │       ├── LiveStartedEvent.java
    │   │   │       └── AssignmentGradedEvent.java
    │   │   ├── security/
    │   │   │   ├── InternalApiFilter.java
    │   │   │   ├── JwksLoader.java
    │   │   │   ├── NotificationJwtValidator.java
    │   │   │   └── JwtSecurityUtils.java
    │   │   ├── service/
    │   │   │   ├── NotificationService.java
    │   │   │   ├── EmailChannelService.java
    │   │   │   └── impl/
    │   │   │       ├── NotificationServiceImpl.java
    │   │   │       └── EmailChannelServiceImpl.java
    │   │   ├── spi/
    │   │   │   ├── EmailChannelPlugin.java
    │   │   │   ├── EmailChannelFactory.java
    │   │   │   ├── model/
    │   │   │   │   ├── EmailSendContext.java
    │   │   │   │   └── EmailSendResult.java
    │   │   │   └── plugins/
    │   │   │       ├── MockEmailPlugin.java
    │   │   │       └── SmtpEmailPlugin.java
    │   │   └── support/
    │   │       └── DeliveryTaskJob.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/educloud/notification/
            ├── controller/
            │   ├── NotificationControllerTest.java
            │   └── EmailChannelStatusControllerTest.java
            ├── messaging/
            │   └── DomainNotificationConsumerTest.java
            └── service/
                ├── NotificationServiceTest.java
                └── EmailChannelServiceTest.java
```

---

## 三、数据库物理表结构设计

```sql
-- 1. 通知元数据表
CREATE TABLE IF NOT EXISTS sys_notification (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花算法通知主键 ID',
    title VARCHAR(255) NOT NULL COMMENT '通知标题',
    content TEXT NOT NULL COMMENT '通知正文内容',
    kind VARCHAR(32) NOT NULL COMMENT '通知分类: SYSTEM/COURSE/LIVE/ASSIGNMENT/EXAM/PAYMENT',
    target_type VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '受众类型: USER/ALL/ROLE',
    sender_id BIGINT NULL COMMENT '发信人 ID (系统触发为 0 或 NULL)',
    action_label VARCHAR(64) NULL COMMENT '交互操作文案，如：进入直播/查看作业',
    action_path VARCHAR(255) NULL COMMENT '前端跳转路径，如：/live/1 或 /assignments',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_kind_created (kind, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知主体元数据表';

-- 2. 用户个人收件箱与已读状态隔离表
CREATE TABLE IF NOT EXISTS sys_user_notification (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '收件箱记录 ID',
    user_id BIGINT NOT NULL COMMENT '接收人用户 ID',
    notification_id BIGINT NOT NULL COMMENT '关联通知 ID (sys_notification.id)',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读, 1-已读',
    read_at DATETIME(3) NULL COMMENT '读取时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-已删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_notification (user_id, notification_id),
    INDEX idx_user_unread (user_id, is_deleted, is_read, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收件箱与已读隔离表';

-- 3. 外部渠道投递任务表 (邮件/短信异步重试引擎)
CREATE TABLE IF NOT EXISTS sys_delivery_task (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '投递任务 ID',
    notification_id BIGINT NOT NULL COMMENT '关联通知 ID',
    user_id BIGINT NOT NULL COMMENT '接收人用户 ID',
    channel_code VARCHAR(32) NOT NULL DEFAULT 'EMAIL' COMMENT '渠道: EMAIL/SMS',
    receiver_target VARCHAR(255) NOT NULL COMMENT '接收目标 (脱敏邮箱或手机号)',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retries INT NOT NULL DEFAULT 3 COMMENT '最大允许重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间 (指数退避)',
    last_error_message VARCHAR(500) NULL COMMENT '最后一次失败原因 (脱敏)',
    sent_at DATETIME(3) NULL COMMENT '实际投递成功时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部渠道异步投递任务表';
```

---

## 四、REST API 契约设计

### 1. 用户端通知收件箱接口

- `GET /api/v1/notifications`：
  - 查询参数：`page` (默认 1)、`size` (默认 20)、`kind` (可选)、`unreadOnly` (可选 true/false)
  - 响应：分页列表 `ApiResponse<PageResult<NotificationResponse>>`
- `GET /api/v1/notifications/unread-count`：
  - 响应：`ApiResponse<UnreadCountResponse>`（含 `unreadCount: number`）
- `PUT /api/v1/notifications/{id}/read`：
  - 响应：`ApiResponse<Void>`（标记单条已读，严格校验归属）
- `PUT /api/v1/notifications/read-all`：
  - 响应：`ApiResponse<Void>`（一键标记当前用户全部已读）
- `DELETE /api/v1/notifications/{id}`：
  - 响应：`ApiResponse<Void>`（单条从收件箱移除）

### 2. 管理端接口

- `POST /api/v1/admin/notifications`：
  - 请求体：`PublishNotificationRequest`（`title`, `content`, `kind`, `targetType`, `targetUserIds` / `targetRole`, `actionLabel`, `actionPath`）
  - 权限：`notification:publish` 或 `ADMIN`
- `GET /api/v1/notification-channels/email/status`：
  - 响应：`EmailChannelStatusResponse`（`provider`, `host`, `port`, `username` 脱敏如 `sup***@educloud.cn`, `enabled: true`, `passwordConfigured: true`）
- `POST /api/v1/notification-channels/email/test-send`：
  - 频控：Redis 60 秒限 1 次
  - 限制：仅能发往当前登录管理员自身已绑定的已验证邮箱

### 3. 微服务内部接口

- `POST /internal/v1/notifications/send`：
  - 认证：`X-Internal-Token: educloud-internal-secret`
  - 请求体：`InternalSendNotificationRequest`

---

## 五、安全与 RBAC 权限设计

1. **权限码定义**：
   - `notification:publish`（ID: 151）：管理员发布全员公告与定向广播
   - `notification:channel:view`（ID: 152）：查看邮件/短信通道配置状态
   - `notification:channel:test`（ID: 153）：触发通道自测发信
2. **IDOR 水平越权防范**：
   - 标记已读、删除通知严格限定 `WHERE user_id = :currentUserId`，跨用户操作直接拦截并抛出 `403 ACCESS_DENIED`。
3. **SMTP 凭据安全**：
   - 严禁在任何 API 响应中包含明文 SMTP 密码；测试发信不允许由前端任意指定收件人邮箱（防开放式邮件中继垃圾攻击）。

---

## 六、异步事件消费与投递重试机制

1. **RabbitMQ 事件监听**：
   - 监听 `payment.succeeded` -> 自动创建“课程购买成功”通知与收件箱记录，并排队生成邮件投递任务；
   - 监听 `order.refunded` -> 自动创建“订单退款已完成”通知；
   - 监听 `live.started` -> 自动向该课程在读学员广播“直播课堂已开播”；
   - 监听 `assignment.graded` -> 自动向学员发送“作业批改完成”通知。
2. **DeliveryTaskJob 调度器**：
   - 周期性扫描 `sys_delivery_task` 状态为 `PENDING` 且 `next_retry_at <= NOW()` 的记录；
   - 指数退避重试（1m -> 5m -> 15m），重试达到上限自动标记为 `FAILED`；
   - 投递失败仅记录脱敏审计日志，绝不影响主系统其他微服务的业务流程。
