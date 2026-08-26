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
public class CourseDomainEvent {
    private String eventId;
    private String eventType; // e.g. CoursePublished, CourseCreated
    private String courseId;
    private String title;
    private String teacherId;
    private String categoryId;
    private Long priceCents;
    private LocalDateTime occurredAt;
}
