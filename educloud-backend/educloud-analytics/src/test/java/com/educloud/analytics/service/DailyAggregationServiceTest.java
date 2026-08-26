package com.educloud.analytics.service;

import com.educloud.analytics.mapper.CourseEngagementStatsMapper;
import com.educloud.analytics.mapper.DailyFinanceMetricsMapper;
import com.educloud.analytics.mapper.DailyPlatformMetricsMapper;
import com.educloud.analytics.mapper.DailyTeacherMetricsMapper;
import com.educloud.analytics.service.impl.DailyAggregationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DailyAggregationServiceTest {

    @Mock
    private DailyTeacherMetricsMapper teacherMetricsMapper;

    @Mock
    private DailyPlatformMetricsMapper platformMetricsMapper;

    @Mock
    private DailyFinanceMetricsMapper financeMetricsMapper;

    @Mock
    private CourseEngagementStatsMapper courseEngagementStatsMapper;

    @InjectMocks
    private DailyAggregationServiceImpl dailyAggregationService;

    @Test
    @DisplayName("测试用户注册增量触发")
    void testRecordUserRegistered() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        dailyAggregationService.recordUserRegistered(date);

        verify(platformMetricsMapper, times(1)).upsertIncrement(date, 1, 0, 0, 0L, 0L);
    }

    @Test
    @DisplayName("测试选课事件多表原子累加")
    void testRecordEnrollment() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        dailyAggregationService.recordEnrollment("course_101", "Spring Cloud", "teacher_01", 19900L, date);

        verify(teacherMetricsMapper, times(1)).upsertIncrement("teacher_01", date, 1, 19900L, 1, 0);
        verify(platformMetricsMapper, times(1)).upsertIncrement(date, 0, 0, 1, 19900L, 0L);
        verify(financeMetricsMapper, times(1)).upsertIncrement(date, 19900L, 0L, 19900L, 1, 0);
        verify(courseEngagementStatsMapper, times(1)).upsertCourseStats("course_101", "Spring Cloud", "teacher_01", 1, 0);
    }

    @Test
    @DisplayName("测试退款事件财务与运营冲正")
    void testRecordRefund() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        dailyAggregationService.recordRefund(9900L, date);

        verify(platformMetricsMapper, times(1)).upsertIncrement(date, 0, 0, 0, 0L, 9900L);
        verify(financeMetricsMapper, times(1)).upsertIncrement(date, 0L, 9900L, -9900L, 0, 1);
    }
}
