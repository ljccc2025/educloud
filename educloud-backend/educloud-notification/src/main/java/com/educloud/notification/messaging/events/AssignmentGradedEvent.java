package com.educloud.notification.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentGradedEvent {
    private String eventId;
    private Long assignmentId;
    private Long userId;
    private Long courseId;
    private BigDecimal score;
    private String assignmentTitle;
    private String feedback;
}
