package com.educloud.analytics.dto.response.teacher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherStatsResponse {
    private Integer totalCourses;
    private Integer totalStudents;
    private Double totalRevenue;
    private Double completionRate;
}
