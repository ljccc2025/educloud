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
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainNotificationConsumer {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String IDEMPOTENCY_PREFIX = "educloud:notification:processed-event:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    @RabbitListener(queues = RabbitMqConfiguration.NOTIFICATION_QUEUE_NAME)
    public void onMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        log.info("[DomainNotificationConsumer] Received event: routingKey={}, body={}", routingKey, body);

        try {
            JsonNode root = objectMapper.readTree(body);
            String eventId = root.has("eventId") ? root.get("eventId").asText() : null;

            if (eventId != null && !tryAcquireIdempotency(eventId)) {
                log.warn("[DomainNotificationConsumer] Duplicate event ignored: eventId={}", eventId);
                return;
            }

            if (RabbitMqConfiguration.ROUTING_KEY_PAYMENT_SUCCEEDED.equals(routingKey)) {
                PaymentSucceededEvent event = objectMapper.treeToValue(root, PaymentSucceededEvent.class);
                handlePaymentSucceeded(event);
            } else if (RabbitMqConfiguration.ROUTING_KEY_ORDER_REFUNDED.equals(routingKey)) {
                OrderRefundedEvent event = objectMapper.treeToValue(root, OrderRefundedEvent.class);
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
            log.error("[DomainNotificationConsumer] Failed to process message", e);
        }
    }

    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        if (event.getUserId() == null) return;
        String title = "课程购买成功";
        String content = "您已成功购买《" + (event.getCourseTitle() != null ? event.getCourseTitle() : "精选课程") + "》，支付金额 ¥" + (event.getAmount() != null ? event.getAmount() : "0.00") + "。现在可以开始学习了！";
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

    private boolean tryAcquireIdempotency(String eventId) {
        String key = IDEMPOTENCY_PREFIX + eventId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(success);
    }
}
