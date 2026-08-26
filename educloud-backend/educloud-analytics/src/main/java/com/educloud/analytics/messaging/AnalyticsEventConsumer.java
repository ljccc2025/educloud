package com.educloud.analytics.messaging;

import com.educloud.analytics.config.RabbitMqConfig;
import com.educloud.analytics.entity.AnalyticsEventInboxEntity;
import com.educloud.analytics.mapper.AnalyticsEventInboxMapper;
import com.educloud.analytics.messaging.event.*;
import com.educloud.analytics.service.DailyAggregationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsEventConsumer {

    private final DailyAggregationService dailyAggregationService;
    private final AnalyticsEventInboxMapper eventInboxMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ANALYTICS_USER)
    public void onUserEvent(UserDomainEvent event) {
        log.info("Received UserDomainEvent: {}", event);
        if (event == null || !checkAndSaveInbox(event.getEventId(), event.getEventType(), "educloud-user", event)) {
            return;
        }

        LocalDate date = (event.getOccurredAt() != null) ? event.getOccurredAt().toLocalDate() : LocalDate.now();
        if ("UserRegistered".equalsIgnoreCase(event.getEventType()) || "UserCreated".equalsIgnoreCase(event.getEventType())) {
            dailyAggregationService.recordUserRegistered(date);
        }
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ANALYTICS_COURSE)
    public void onCourseEvent(CourseDomainEvent event) {
        log.info("Received CourseDomainEvent: {}", event);
        if (event == null || !checkAndSaveInbox(event.getEventId(), event.getEventType(), "educloud-course", event)) {
            return;
        }

        LocalDate date = (event.getOccurredAt() != null) ? event.getOccurredAt().toLocalDate() : LocalDate.now();
        if ("CoursePublished".equalsIgnoreCase(event.getEventType()) || "CourseCreated".equalsIgnoreCase(event.getEventType())) {
            dailyAggregationService.recordCoursePublished(event.getCourseId(), event.getTitle(), event.getTeacherId(), date);
        }
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ANALYTICS_PAYMENT)
    public void onPaymentEvent(PaymentDomainEvent event) {
        log.info("Received PaymentDomainEvent: {}", event);
        if (event == null || !checkAndSaveInbox(event.getEventId(), event.getEventType(), "educloud-payment", event)) {
            return;
        }

        LocalDate date = (event.getOccurredAt() != null) ? event.getOccurredAt().toLocalDate() : LocalDate.now();
        String type = event.getEventType();
        if ("PaymentSuccess".equalsIgnoreCase(type) || "OrderPaid".equalsIgnoreCase(type)) {
            long amount = (event.getAmountCents() != null) ? event.getAmountCents() : 0L;
            dailyAggregationService.recordEnrollment(event.getCourseId(), event.getCourseTitle(), event.getTeacherId(), amount, date);
        } else if ("RefundCompleted".equalsIgnoreCase(type) || "RefundSuccess".equalsIgnoreCase(type)) {
            long amount = (event.getAmountCents() != null) ? event.getAmountCents() : 0L;
            dailyAggregationService.recordRefund(amount, date);
        }
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ANALYTICS_CONTENT)
    public void onContentEvent(ContentDomainEvent event) {
        log.info("Received ContentDomainEvent: {}", event);
        if (event == null || !checkAndSaveInbox(event.getEventId(), event.getEventType(), "educloud-content", event)) {
            return;
        }

        LocalDate date = (event.getOccurredAt() != null) ? event.getOccurredAt().toLocalDate() : LocalDate.now();
        boolean completed = Boolean.TRUE.equals(event.getCompleted()) || "CourseCompleted".equalsIgnoreCase(event.getEventType());
        dailyAggregationService.recordCourseProgress(event.getCourseId(), event.getTeacherId(), completed, date);
    }

    private boolean checkAndSaveInbox(String eventId, String eventType, String service, Object payload) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        try {
            String json = (payload != null) ? objectMapper.writeValueAsString(payload) : "{}";
            AnalyticsEventInboxEntity inbox = AnalyticsEventInboxEntity.builder()
                    .eventId(eventId)
                    .eventType(eventType != null ? eventType : "UNKNOWN")
                    .sourceService(service)
                    .payloadJson(json)
                    .status("PROCESSED")
                    .createdAt(LocalDateTime.now())
                    .build();
            int inserted = eventInboxMapper.insertIfNotExists(inbox);
            if (inserted <= 0) {
                log.warn("Duplicate event ignored by inbox: eventId={}", eventId);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to check inbox for event: {}", eventId, e);
            return true;
        }
    }
}
