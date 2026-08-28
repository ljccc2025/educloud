package com.educloud.notification.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamGradedEvent {
    private String eventId;
    private Long examId;
    private Long userId;
    private Long studentId;
    private Long courseId;
    private String examTitle;
    private String courseTitle;
    private Integer score;
    private Boolean passed;
}
