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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("支付成功事件无课程名时跨库解析真实课程名测试")
    void handlePaymentSucceededResolvesRealCourseTitle() {
        when(jdbcTemplate.queryForList(
                "SELECT course_title_snapshot FROM educloud_order.trade_order_item WHERE order_id = ? LIMIT 1",
                String.class, 123L)).thenReturn(List.of("React 18 从入门到精通"));

        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                .eventId("evt_pay_1002")
                .orderId(123L)
                .userId(1L)
                .amount(new BigDecimal("299.00"))
                .build();

        consumer.handlePaymentSucceeded(event);

        verify(jdbcTemplate).queryForList(
                "SELECT course_title_snapshot FROM educloud_order.trade_order_item WHERE order_id = ? LIMIT 1",
                String.class, 123L);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendDirectNotification(
                eq(1L),
                eq(NotificationKind.PAYMENT),
                eq("课程购买成功"),
                contentCaptor.capture(),
                eq("开始学习"),
                eq("/my-courses"),
                eq(true)
        );
        assertThat(contentCaptor.getValue())
                .contains("React 18 从入门到精通")
                .doesNotContain("精选课程");
    }

    @Test
    @DisplayName("跨库查询失败时兜底为已购课程测试")
    void handlePaymentSucceededFallsBackWhenCourseTitleQueryFails() {
        when(jdbcTemplate.queryForList(
                "SELECT course_title_snapshot FROM educloud_order.trade_order_item WHERE order_id = ? LIMIT 1",
                String.class, 456L)).thenThrow(new RuntimeException("connection refused"));

        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                .eventId("evt_pay_1003")
                .orderId(456L)
                .userId(2L)
                .amount(new BigDecimal("99.00"))
                .build();

        consumer.handlePaymentSucceeded(event);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendDirectNotification(
                eq(2L),
                eq(NotificationKind.PAYMENT),
                eq("课程购买成功"),
                contentCaptor.capture(),
                eq("开始学习"),
                eq("/my-courses"),
                eq(true)
        );
        assertThat(contentCaptor.getValue())
                .contains("已购课程")
                .doesNotContain("精选课程");
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
                .audienceIds(List.of(3001L, 3002L))
                .build();

        consumer.handleLiveStarted(liveEvent);
        // 事件自带报名受众时逐个发送
        verify(notificationService).sendDirectNotification(
                eq(3001L), eq(NotificationKind.LIVE), contains("直播课堂"), contains("李明远老师"), eq("进入直播"), eq("/live/101"), eq(false)
        );
        verify(notificationService).sendDirectNotification(
                eq(3002L), eq(NotificationKind.LIVE), contains("直播课堂"), contains("李明远老师"), eq("进入直播"), eq("/live/101"), eq(false)
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

    @Test
    @DisplayName("开播事件无受众时跨库查询报名学生发送测试")
    void handleLiveStartedResolvesAudienceFromCourseEnrollment() {
        when(jdbcTemplate.queryForList(
                "SELECT student_id FROM educloud_course.course_enrollment WHERE course_id = ? AND status = 'ACTIVE'",
                Long.class, 101L)).thenReturn(List.of(3001L, 3002L, 3003L));

        LiveStartedEvent liveEvent = LiveStartedEvent.builder()
                .eventId("evt_live_3002")
                .roomId(101L)
                .courseId(101L)
                .courseTitle("微服务实战")
                .teacherName("李明远老师")
                .build();

        consumer.handleLiveStarted(liveEvent);

        verify(jdbcTemplate).queryForList(
                "SELECT student_id FROM educloud_course.course_enrollment WHERE course_id = ? AND status = 'ACTIVE'",
                Long.class, 101L);
        verify(notificationService, times(3)).sendDirectNotification(
                any(), eq(NotificationKind.LIVE), contains("直播课堂"), contains("微服务实战"), eq("进入直播"), eq("/live/101"), eq(false)
        );
        verify(notificationService).sendDirectNotification(
                eq(3002L), eq(NotificationKind.LIVE), contains("直播课堂"), contains("微服务实战"), eq("进入直播"), eq("/live/101"), eq(false)
        );
    }

    @Test
    @DisplayName("开播事件无人报名时降级不发送测试")
    void handleLiveStartedSkipsWhenNoAudience() {
        // 事件无受众且课程无 ACTIVE 报名学生
        when(jdbcTemplate.queryForList(
                "SELECT student_id FROM educloud_course.course_enrollment WHERE course_id = ? AND status = 'ACTIVE'",
                Long.class, 999L)).thenReturn(List.of());

        LiveStartedEvent liveEvent = LiveStartedEvent.builder()
                .eventId("evt_live_3003")
                .roomId(999L)
                .courseId(999L)
                .courseTitle("无人报名课")
                .build();

        consumer.handleLiveStarted(liveEvent);

        verify(notificationService, never()).sendDirectNotification(
                any(), eq(NotificationKind.LIVE), any(), any(), any(), any(), eq(false)
        );

        // 跨库查询异常同样降级不发送
        when(jdbcTemplate.queryForList(
                "SELECT student_id FROM educloud_course.course_enrollment WHERE course_id = ? AND status = 'ACTIVE'",
                Long.class, 998L)).thenThrow(new RuntimeException("connection refused"));
        LiveStartedEvent noCourse = LiveStartedEvent.builder()
                .eventId("evt_live_3004")
                .roomId(998L)
                .courseId(998L)
                .build();
        consumer.handleLiveStarted(noCourse);
        verify(notificationService, never()).sendDirectNotification(
                any(), eq(NotificationKind.LIVE), any(), any(), any(), any(), eq(false)
        );
    }
}
