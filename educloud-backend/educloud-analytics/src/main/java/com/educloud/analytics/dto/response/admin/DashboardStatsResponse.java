package com.educloud.analytics.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private Integer totalUsers;
    private Double userGrowthRate;
    private Integer totalCourses;
    private Double courseGrowthRate;
    private Double totalRevenue;
    private Double revenueGrowthRate;
    private Integer activeLives;
}
