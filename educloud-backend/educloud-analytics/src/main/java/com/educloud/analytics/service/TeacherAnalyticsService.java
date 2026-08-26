package com.educloud.analytics.service;

import com.educloud.analytics.dto.response.teacher.*;

import java.util.List;

public interface TeacherAnalyticsService {

    TeacherStatsResponse getStats(String teacherId);

    List<EnrollmentTrendItem> getEnrollmentTrend(String teacherId);

    List<RevenueTrendItem> getRevenueTrend(String teacherId);

    List<EngagementItem> getEngagement(String teacherId);

    List<TeacherActivityItem> getActivities(String teacherId);
}
