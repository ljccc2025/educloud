package com.educloud.analytics.service;

import com.educloud.analytics.dto.response.admin.DashboardStatsResponse;
import com.educloud.analytics.dto.response.admin.DistributionsResponse;
import com.educloud.analytics.dto.response.admin.UserGrowthItem;
import com.educloud.analytics.entity.DailyPlatformMetricsEntity;
import com.educloud.analytics.mapper.AuditEventReadModelMapper;
import com.educloud.analytics.mapper.DailyPlatformMetricsMapper;
import com.educloud.analytics.service.impl.AdminAnalyticsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock
    private DailyPlatformMetricsMapper platformMetricsMapper;

    @Mock
    private AuditEventReadModelMapper auditEventReadModelMapper;

    @InjectMocks
    private AdminAnalyticsServiceImpl adminAnalyticsService;

    @Test
    @DisplayName("测试管理端平台大屏统计概览")
    void testGetDashboardStats() {
        when(platformMetricsMapper.selectPlatformSummary()).thenReturn(Map.of(
                "total_users", 30000,
                "total_courses", 180,
                "total_gmv_cents", 200000000L
        ));

        DashboardStatsResponse stats = adminAnalyticsService.getDashboardStats();

        assertThat(stats.getTotalUsers()).isEqualTo(30000);
        assertThat(stats.getTotalCourses()).isEqualTo(180);
        assertThat(stats.getTotalRevenue()).isEqualTo(2000000.0);
        assertThat(stats.getActiveLives()).isEqualTo(3);
    }

    @Test
    @DisplayName("测试管理端用户与课程增长双轴数据构建")
    void testGetUserGrowth() {
        LocalDate today = LocalDate.now();
        when(platformMetricsMapper.selectDateRange(any(), any())).thenReturn(List.of(
                DailyPlatformMetricsEntity.builder().metricDate(today).totalUsers(29000).totalCourses(160).build()
        ));

        List<UserGrowthItem> growth = adminAnalyticsService.getUserGrowth();

        assertThat(growth).hasSize(6);
        assertThat(growth.get(5).getUsers()).isEqualTo(29000);
        assertThat(growth.get(5).getCourses()).isEqualTo(160);
    }

    @Test
    @DisplayName("测试管理端分类分布与订单状态分布")
    void testGetDistributions() {
        DistributionsResponse resp = adminAnalyticsService.getDistributions();

        assertThat(resp.getCategories()).isNotEmpty();
        assertThat(resp.getOrderStatuses()).isNotEmpty();
        assertThat(resp.getCategories().get(0).getName()).isEqualTo("后端开发");
    }
}
