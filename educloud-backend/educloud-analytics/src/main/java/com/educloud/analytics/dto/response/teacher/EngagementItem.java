package com.educloud.analytics.dto.response.teacher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngagementItem {
    private String courseId;
    private String courseTitle;
    private Integer totalEnrollments;
    private Integer activeLearners;
    private Integer completedCount;
    private Double completionRate;
    private Double avgRating;
}
