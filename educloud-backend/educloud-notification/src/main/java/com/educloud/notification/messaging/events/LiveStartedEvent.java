package com.educloud.notification.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    /** 报名学生 ID 列表：事件自包含时由 live 服务在开播时写入；缺失时消费端跨库查询报名表 */
    private List<Long> audienceIds;
}
