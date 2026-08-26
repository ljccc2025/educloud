package com.educloud.analytics.dto.response.teacher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherActivityItem {
    private String id;
    private String studentName;
    private String action;
    private String courseName;
    private String timeAgo;
    private String timestamp;
}
