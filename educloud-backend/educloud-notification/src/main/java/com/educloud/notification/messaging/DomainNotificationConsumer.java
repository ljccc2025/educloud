package com.educloud.notification.messaging;

import com.educloud.notification.config.RabbitMqConfiguration;
import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.messaging.events.AssignmentGradedEvent;
import com.educloud.notification.messaging.events.LiveStartedEvent;
import com.educloud.notification.messaging.events.OrderRefundedEvent;
import com.educloud.notification.messaging.events.PaymentSucceededEvent;
import com.educloud.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainNotificationConsumer {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final String IDEMPOTENCY_PREFIX = "educloud:notification:processed-event:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    @RabbitListener(queues = {RabbitMqConfiguration.NOTIFICATION_QUEUE_NAME, RabbitMqConfiguration.NOTIFICATION_PAYMENT_QUEUE_NAME})
    public void onMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        log.info("[DomainNotificationConsumer] Received event: routingKey={}, body={}", routingKey, body);

        String eventId = null;
        try {
            JsonNode root = objectMapper.readTree(body);
            eventId = root.has("eventId") ? root.get("eventId").asText() : null;
            // M10 修复：payment 发的事件 body 无 eventId 字段，用 routingKey + aggregateId 头合成幂等键
            if (eventId == null) {
                Object aggregateId = message.getMessageProperties().getHeader("aggregateId");
                if (aggregateId != null) {
                    eventId = routingKey + ":" + aggregateId;
                }
            }

            if (eventId != null && !tryAcquireIdempotency(eventId)) {
                log.warn("[DomainNotificationConsumer] Duplicate event ignored: eventId={}", eventId);
                return;
            }

            if (RabbitMqConfiguration.ROUTING_KEY_PAYMENT_SUCCEEDED.equals(routingKey)) {
                PaymentSucceededEvent event = objectMapper.treeToValue(root, PaymentSucceededEvent.class);
                // 兼容 payment 发件箱事件：amountCents（分）→ amount（元）
                if (event.getAmount() == null && root.has("amountCents")) {
                    event.setAmount(BigDecimal.valueOf(root.get("amountCents").asLong(), 2));
                }
                handlePaymentSucceeded(event);
            } else if (RabbitMqConfiguration.ROUTING_KEY_ORDER_REFUNDED.equals(routingKey)
                    || RabbitMqConfiguration.ROUTING_KEY_PAYMENT_REFUNDED.equals(routingKey)) {
                OrderRefundedEvent event = objectMapper.treeToValue(root, OrderRefundedEvent.class);
                // 兼容 payment 发件箱事件：refundAmountCents（分）→ amount（元）
                if (event.getAmount() == null && root.has("refundAmountCents")) {
                    event.setAmount(BigDecimal.valueOf(root.get("refundAmountCents").asLong(), 2));
                }
                handleOrderRefunded(event);
            } else if (RabbitMqConfiguration.ROUTING_KEY_LIVE_STARTED.equals(routingKey)) {
                LiveStartedEvent event = objectMapper.treeToValue(root, LiveStartedEvent.class);
                handleLiveStarted(event);
            } else if (RabbitMqConfiguration.ROUTING_KEY_ASSIGNMENT_GRADED.equals(routingKey)) {
                AssignmentGradedEvent event = objectMapper.treeToValue(root, AssignmentGradedEvent.class);
                handleAssignmentGraded(event);
            } else {
                log.info("[DomainNotificationConsumer] Unhandled routingKey: {}", routingKey);
            }
        } catch (Exception e) {
            // 失败后释放幂等键：避免“消息已确认 + 幂等键残留”导致该事件永久不可重放；
            // 正式修复应配置 DLQ 与重试容器，当前以日志告警 + 保留重放可能性兜底
            if (eventId != null) {
                redisTemplate.delete(IDEMPOTENCY_PREFIX + eventId);
            }
            log.error("[DomainNotificationConsumer] Failed to process message, idempotency key released for retry", e);
        }
    }

    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        if (event.getUserId() == null) return;
        String title = "课程购买成功";
        // 事件自带课程名优先；payment 事件恒不带 courseTitle，则跨库直查订单项标题快照
        String courseTitle = event.getCourseTitle() != null
                ? event.getCourseTitle()
                : resolveCourseTitle(event.getOrderId());
        String content = "您已成功购买《" + (courseTitle != null ? courseTitle : "已购课程") + "》，支付金额 ¥" + (event.getAmount() != null ? event.getAmount() : "0.00") + "。现在可以开始学习了！";
        String actionPath = event.getCourseId() != null ? "/learn/" + event.getCourseId() : "/my-courses";
        notificationService.sendDirectNotification(
                event.getUserId(),
                NotificationKind.PAYMENT,
                title,
                content,
                "开始学习",
                actionPath,
                true // 自动排队发送邮件凭据
        );
    }

    public void handleOrderRefunded(OrderRefundedEvent event) {
        if (event.getUserId() == null) return;
        String title = "订单退款已完成";
        String content = "您的订单退款已原路退回，退款金额 ¥" + (event.getAmount() != null ? event.getAmount() : "0.00") + "。原因：" + (event.getRefundReason() != null ? event.getRefundReason() : "协商一致退款");
        notificationService.sendDirectNotification(
                event.getUserId(),
                NotificationKind.SYSTEM,
                title,
                content,
                "查看订单",
                "/orders",
                false
        );
    }

    public void handleLiveStarted(LiveStartedEvent event) {
        if (event.getRoomId() == null) return;
        String title = "直播课堂已开播";
        String content = (event.getTeacherName() != null ? event.getTeacherName() : "讲师") + " 的课程《" + (event.getCourseTitle() != null ? event.getCourseTitle() : "直播互动课") + "》正在直播中，点击立即进入教室！";
        String actionPath = "/live/" + event.getRoomId();
        // 演示广播给活跃学员
        notificationService.sendDirectNotification(
                2091648316809035778L, // fe_demo_10
                NotificationKind.LIVE,
                title,
                content,
                "进入直播",
                actionPath,
                false
        );
    }

    public void handleAssignmentGraded(AssignmentGradedEvent event) {
        if (event.getUserId() == null) return;
        String title = "作业批改完成";
        String content = "您的作业《" + (event.getAssignmentTitle() != null ? event.getAssignmentTitle() : "课后习题") + "》已批改完成，本次得分：" + (event.getScore() != null ? event.getScore() : "100") + "分。";
        notificationService.sendDirectNotification(
                event.getUserId(),
                NotificationKind.ASSIGNMENT,
                title,
                content,
                "查看作业",
                "/assignments",
                false
        );
    }

    /** 跨库解析真实课程名：educloud_order.trade_order_item 标题快照（订单多课程取第一条）；异常返回 null */
    private String resolveCourseTitle(Long orderId) {
        if (orderId == null) return null;
        try {
            List<String> titles = jdbcTemplate.queryForList(
                    "SELECT course_title_snapshot FROM educloud_order.trade_order_item WHERE order_id = ? LIMIT 1",
                    String.class, orderId);
            return titles.isEmpty() ? null : titles.get(0);
        } catch (Exception e) {
            log.warn("Resolve course title from educloud_order failed, orderId={}: {}", orderId, e.getMessage());
            return null;
        }
    }

    private boolean tryAcquireIdempotency(String eventId) {
        String key = IDEMPOTENCY_PREFIX + eventId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(success);
    }
}
