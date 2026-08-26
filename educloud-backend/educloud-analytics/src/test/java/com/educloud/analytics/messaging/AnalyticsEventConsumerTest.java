package com.educloud.analytics.messaging;

import com.educloud.analytics.mapper.AnalyticsEventInboxMapper;
import com.educloud.analytics.messaging.event.*;
import com.educloud.analytics.service.DailyAggregationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    @Mock
    private DailyAggregationService dailyAggregationService;

    @Mock
    private AnalyticsEventInboxMapper eventInboxMapper;

    private ObjectMapper objectMapper;

    private AnalyticsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        consumer = new AnalyticsEventConsumer(dailyAggregationService, eventInboxMapper, objectMapper);
    }

    @Test
    @DisplayName("测试用户注册事件消费与幂等防重")
    void testOnUserEvent() {
        when(eventInboxMapper.insertIfNotExists(any())).thenReturn(1);

        UserDomainEvent event = UserDomainEvent.builder()
                .eventId("EVT_USER_001")
                .eventType("UserRegistered")
                .userId(1001L)
                .username("test_student")
                .occurredAt(LocalDateTime.of(2026, 8, 26, 10, 0))
                .build();

        consumer.onUserEvent(event);

        verify(dailyAggregationService, times(1)).recordUserRegistered(eq(event.getOccurredAt().toLocalDate()));

        // 重复消费测试：返回 0 则不应再次触发
        when(eventInboxMapper.insertIfNotExists(any())).thenReturn(0);
        consumer.onUserEvent(event);
        verify(dailyAggregationService, times(1)).recordUserRegistered(any());
    }

    @Test
    @DisplayName("测试支付成功事件增量记录")
    void testOnPaymentEventSuccess() {
        when(eventInboxMapper.insertIfNotExists(any())).thenReturn(1);

        PaymentDomainEvent event = PaymentDomainEvent.builder()
                .eventId("EVT_PAY_001")
                .eventType("PaymentSuccess")
                .orderNo("ORD_9901")
                .courseId("course_101")
                .courseTitle("Spring Cloud 微服务")
                .teacherId("teacher_01")
                .amountCents(19900L)
                .occurredAt(LocalDateTime.of(2026, 8, 26, 12, 0))
                .build();

        consumer.onPaymentEvent(event);

        verify(dailyAggregationService, times(1)).recordEnrollment(
                eq("course_101"), eq("Spring Cloud 微服务"), eq("teacher_01"), eq(19900L), eq(event.getOccurredAt().toLocalDate())
        );
    }

    @Test
    @DisplayName("测试退款事件消费")
    void testOnPaymentEventRefund() {
        when(eventInboxMapper.insertIfNotExists(any())).thenReturn(1);

        PaymentDomainEvent event = PaymentDomainEvent.builder()
                .eventId("EVT_PAY_002")
                .eventType("RefundCompleted")
                .amountCents(9900L)
                .occurredAt(LocalDateTime.of(2026, 8, 26, 14, 0))
                .build();

        consumer.onPaymentEvent(event);

        verify(dailyAggregationService, times(1)).recordRefund(eq(9900L), eq(event.getOccurredAt().toLocalDate()));
    }
}
