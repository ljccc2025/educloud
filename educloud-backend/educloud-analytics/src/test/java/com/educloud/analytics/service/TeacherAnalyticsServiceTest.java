package com.educloud.analytics.service;

import com.educloud.analytics.dto.response.teacher.*;
import com.educloud.analytics.entity.CourseEngagementStatsEntity;
import com.educloud.analytics.mapper.AuditEventReadModelMapper;
import com.educloud.analytics.mapper.CourseEngagementStatsMapper;
import com.educloud.analytics.mapper.DailyTeacherMetricsMapper;
import com.educloud.analytics.service.impl.TeacherAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherAnalyticsServiceTest {

    @Mock
    private DailyTeacherMetricsMapper teacherMetricsMapper;

    @Mock
    private CourseEngagementStatsMapper courseEngagementStatsMapper;

    @Mock
    private AuditEventReadModelMapper auditEventReadModelMapper;

    @InjectMocks
    private TeacherAnalyticsServiceImpl teacherAnalyticsService;

    @Test
    @DisplayName("测试教师端统计概览指标查询与百分比换算")
    void testGetStats() {
        when(courseEngagementStatsMapper.selectCount(any())).thenReturn(15L);
        when(teacherMetricsMapper.selectTeacherSummary("teacher_01")).thenReturn(Map.of(
                "total_students", 2000,
                "total_revenue_cents", 50000000L,
                "total_completed_courses", 1600
        ));

        TeacherStatsResponse stats = teacherAnalyticsService.getStats("teacher_01");

        assertThat(stats.getTotalCourses()).isEqualTo(15);
        assertThat(stats.getTotalStudents()).isEqualTo(2000);
        assertThat(stats.getTotalRevenue()).isEqualTo(500000.0);
        assertThat(stats.getCompletionRate()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("测试教师端近 6 个月报名趋势对齐与补零")
    void testGetEnrollmentTrend() {
        when(teacherMetricsMapper.selectMonthlyEnrollmentTrend(eq("teacher_01"), any(), any())).thenReturn(List.of(
                Map.of("month", "2026-08", "total_enrollments", 520)
        ));

        List<EnrollmentTrendItem> trend = teacherAnalyticsService.getEnrollmentTrend("teacher_01");

        assertThat(trend).hasSize(6);
        assertThat(trend.get(5).getEnrollments()).isEqualTo(520);
    }

    @Test
    @DisplayName("测试教师端热门课程深度排行")
    void testGetEngagement() {
        when(courseEngagementStatsMapper.selectTopRankedCourses(eq("teacher_01"), eq(10))).thenReturn(List.of(
                CourseEngagementStatsEntity.builder()
                        .courseId("c_1")
                        .courseTitle("Spring Cloud 实战")
                        .totalEnrollments(800)
                        .activeLearners(600)
                        .completedCount(640)
                        .completionRate(new BigDecimal("80.00"))
                        .avgRating(new BigDecimal("4.95"))
                        .build()
        ));

        List<EngagementItem> list = teacherAnalyticsService.getEngagement("teacher_01");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCourseTitle()).isEqualTo("Spring Cloud 实战");
        assertThat(list.get(0).getCompletionRate()).isEqualTo(80.0);
    }
}
