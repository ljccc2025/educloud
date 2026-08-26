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
public class ContentDomainEvent {
    private String eventId;
    private String eventType; // e.g. LessonProgressUpdated, CourseCompleted
    private String courseId;
    private String lessonId;
    private String studentId;
    private String teacherId;
    private Boolean completed;
    private LocalDateTime occurredAt;
}
