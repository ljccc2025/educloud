package com.educloud.analytics.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDomainEvent {
    private String eventId;
    private String eventType; // e.g. PaymentSuccess, RefundCompleted, EnrollmentCreated
    private String orderNo;
    private String courseId;
    private String courseTitle;
    private String teacherId;
    private String studentId;
    private Long amountCents;
    private LocalDateTime occurredAt;
}
