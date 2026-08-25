package com.educloud.notification.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveStartedEvent {
    private String eventId;
    private Long roomId;
    private Long courseId;
    private String courseTitle;
    private Long teacherId;
    private String teacherName;
}
