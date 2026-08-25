package com.educloud.notification.messaging;

import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.messaging.events.AssignmentGradedEvent;
import com.educloud.notification.messaging.events.LiveStartedEvent;
import com.educloud.notification.messaging.events.OrderRefundedEvent;
import com.educloud.notification.messaging.events.PaymentSucceededEvent;
import com.educloud.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainNotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DomainNotificationConsumer consumer;

    @Test
    @DisplayName("处理支付成功事件生成站内信与邮件任务测试")
    void testHandlePaymentSucceeded() {
        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                .eventId("evt_pay_1001")
                .orderId(8001L)
                .userId(3001L)
                .amount(new BigDecimal("89.00"))
                .courseId(101L)
                .courseTitle("SQL数据分析实战")
                .build();

        consumer.handlePaymentSucceeded(event);

        verify(notificationService, times(1)).sendDirectNotification(
                eq(3001L),
                eq(NotificationKind.PAYMENT),
                eq("课程购买成功"),
                contains("SQL数据分析实战"),
                eq("开始学习"),
                eq("/learn/101"),
                eq(true)
        );
    }

    @Test
    @DisplayName("处理订单退款事件测试")
    void testHandleOrderRefunded() {
        OrderRefundedEvent event = OrderRefundedEvent.builder()
                .eventId("evt_ref_2001")
                .orderId(8002L)
                .userId(3001L)
                .amount(new BigDecimal("89.00"))
                .refundReason("用户主动申请退款")
                .build();

        consumer.handleOrderRefunded(event);

        verify(notificationService, times(1)).sendDirectNotification(
                eq(3001L),
                eq(NotificationKind.SYSTEM),
                eq("订单退款已完成"),
                contains("用户主动申请退款"),
                eq("查看订单"),
                eq("/orders"),
                eq(false)
        );
    }

    @Test
    @DisplayName("处理开播与作业批改事件测试")
    void testHandleLiveAndAssignment() {
        LiveStartedEvent liveEvent = LiveStartedEvent.builder()
                .eventId("evt_live_3001")
                .roomId(101L)
                .courseId(101L)
                .courseTitle("微服务实战")
                .teacherName("李明远老师")
                .build();

        consumer.handleLiveStarted(liveEvent);
        verify(notificationService).sendDirectNotification(
                any(), eq(NotificationKind.LIVE), contains("直播课堂"), contains("李明远老师"), any(), any(), eq(false)
        );

        AssignmentGradedEvent assignEvent = AssignmentGradedEvent.builder()
                .eventId("evt_assign_4001")
                .assignmentId(901L)
                .userId(3001L)
                .assignmentTitle("第三章习题")
                .score(new BigDecimal("95.0"))
                .build();

        consumer.handleAssignmentGraded(assignEvent);
        verify(notificationService).sendDirectNotification(
                eq(3001L), eq(NotificationKind.ASSIGNMENT), eq("作业批改完成"), contains("95.0"), any(), any(), eq(false)
        );
    }
}
