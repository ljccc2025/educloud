package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.*;
import com.educloud.analytics.enums.RebuildStage;
import com.educloud.analytics.enums.RebuildStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DailyMetricsMapperTest {

    @Test
    @DisplayName("测试 Rebuild 枚举属性")
    void testRebuildEnums() {
        assertThat(RebuildStatus.RUNNING.getCode()).isEqualTo("RUNNING");
        assertThat(RebuildStatus.SUCCESS.getCode()).isEqualTo("SUCCESS");
        assertThat(RebuildStatus.FAILED.getCode()).isEqualTo("FAILED");

        assertThat(RebuildStage.INITIALIZING.getCode()).isEqualTo("INITIALIZING");
        assertThat(RebuildStage.USER.getCode()).isEqualTo("USER");
        assertThat(RebuildStage.COURSE.getCode()).isEqualTo("COURSE");
        assertThat(RebuildStage.PAYMENT.getCode()).isEqualTo("PAYMENT");
        assertThat(RebuildStage.COMPLETED.getCode()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("测试 DailyTeacherMetricsEntity 字段完整性")
    void testDailyTeacherMetricsEntity() {
        LocalDate today = LocalDate.now();
        DailyTeacherMetricsEntity entity = DailyTeacherMetricsEntity.builder()
                .id(1L)
                .teacherId("teacher_101")
                .metricDate(today)
                .newEnrollments(25)
                .revenueCents(500000L)
                .activeStudents(80)
                .completedCoursesCount(12)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        assertThat(entity.getTeacherId()).isEqualTo("teacher_101");
        assertThat(entity.getMetricDate()).isEqualTo(today);
        assertThat(entity.getNewEnrollments()).isEqualTo(25);
        assertThat(entity.getRevenueCents()).isEqualTo(500000L);
        assertThat(entity.getActiveStudents()).isEqualTo(80);
        assertThat(entity.getCompletedCoursesCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("测试 DailyPlatformMetricsEntity 字段完整性")
    void testDailyPlatformMetricsEntity() {
        LocalDate today = LocalDate.now();
        DailyPlatformMetricsEntity entity = DailyPlatformMetricsEntity.builder()
                .id(1L)
                .metricDate(today)
                .totalUsers(28450)
                .newUsers(120)
                .totalCourses(156)
                .newCourses(2)
                .totalOrders(88)
                .gmvCents(158420000L)
                .refundCents(3280000L)
                .build();

        assertThat(entity.getTotalUsers()).isEqualTo(28450);
        assertThat(entity.getNewUsers()).isEqualTo(120);
        assertThat(entity.getTotalCourses()).isEqualTo(156);
        assertThat(entity.getGmvCents()).isEqualTo(158420000L);
    }

    @Test
    @DisplayName("测试 DailyFinanceMetricsEntity 字段完整性")
    void testDailyFinanceMetricsEntity() {
        LocalDate today = LocalDate.now();
        DailyFinanceMetricsEntity entity = DailyFinanceMetricsEntity.builder()
                .id(1L)
                .metricDate(today)
                .grossRevenueCents(21500000L)
                .refundAmountCents(380000L)
                .netRevenueCents(21120000L)
                .orderCount(150)
                .refundCount(5)
                .build();

        assertThat(entity.getGrossRevenueCents()).isEqualTo(21500000L);
        assertThat(entity.getNetRevenueCents()).isEqualTo(21120000L);
        assertThat(entity.getOrderCount()).isEqualTo(150);
    }

    @Test
    @DisplayName("测试 CourseEngagementStatsEntity 字段完整性")
    void testCourseEngagementStatsEntity() {
        CourseEngagementStatsEntity entity = CourseEngagementStatsEntity.builder()
                .id(1L)
                .courseId("course_501")
                .courseTitle("Spring Cloud 微服务架构实战")
                .teacherId("teacher_101")
                .totalEnrollments(1240)
                .activeLearners(850)
                .completedCount(1044)
                .completionRate(new BigDecimal("84.20"))
                .avgRating(new BigDecimal("4.90"))
                .build();

        assertThat(entity.getCourseTitle()).isEqualTo("Spring Cloud 微服务架构实战");
        assertThat(entity.getTotalEnrollments()).isEqualTo(1240);
        assertThat(entity.getCompletionRate()).isEqualByComparingTo("84.20");
    }

    @Test
    @DisplayName("测试 Mapper 接口继承 BaseMapper")
    void testMappersAreBaseMappers() {
        assertThat(BaseMapper.class).isAssignableFrom(DailyTeacherMetricsMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(DailyPlatformMetricsMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(DailyFinanceMetricsMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(CourseEngagementStatsMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(AnalyticsRebuildTaskMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(AnalyticsEventInboxMapper.class);
    }
}
