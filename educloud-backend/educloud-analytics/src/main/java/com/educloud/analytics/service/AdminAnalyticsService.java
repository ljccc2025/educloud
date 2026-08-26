package com.educloud.analytics.service;

import com.educloud.analytics.dto.response.admin.DashboardStatsResponse;
import com.educloud.analytics.dto.response.admin.DistributionsResponse;
import com.educloud.analytics.dto.response.admin.UserGrowthItem;
import com.educloud.analytics.dto.response.teacher.TeacherActivityItem;

import java.util.List;

public interface AdminAnalyticsService {

    DashboardStatsResponse getDashboardStats();

    List<UserGrowthItem> getUserGrowth();

    DistributionsResponse getDistributions();

    List<TeacherActivityItem> getRecentActivities();
}
