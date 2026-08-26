package com.educloud.analytics.service.impl;

import com.educloud.analytics.mapper.*;
import com.educloud.analytics.service.DailyAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyAggregationServiceImpl implements DailyAggregationService {

    private final DailyTeacherMetricsMapper teacherMetricsMapper;
    private final DailyPlatformMetricsMapper platformMetricsMapper;
    private final DailyFinanceMetricsMapper financeMetricsMapper;
    private final CourseEngagementStatsMapper courseEngagementStatsMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordUserRegistered(LocalDate date) {
        LocalDate metricDate = (date != null) ? date : LocalDate.now();
        platformMetricsMapper.upsertIncrement(metricDate, 1, 0, 0, 0L, 0L);
        log.info("Recorded UserRegistered for date {}", metricDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordCoursePublished(String courseId, String courseTitle, String teacherId, LocalDate date) {
        LocalDate metricDate = (date != null) ? date : LocalDate.now();
        platformMetricsMapper.upsertIncrement(metricDate, 0, 1, 0, 0L, 0L);
        if (courseId != null) {
            courseEngagementStatsMapper.upsertCourseStats(
                    courseId,
                    (courseTitle != null) ? courseTitle : "未命名课程",
                    (teacherId != null) ? teacherId : "teacher_system",
                    0,
                    0
            );
        }
        log.info("Recorded CoursePublished: courseId={}, teacherId={}, date={}", courseId, teacherId, metricDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordEnrollment(String courseId, String courseTitle, String teacherId, long priceCents, LocalDate date) {
        LocalDate metricDate = (date != null) ? date : LocalDate.now();
        if (teacherId != null) {
            teacherMetricsMapper.upsertIncrement(teacherId, metricDate, 1, priceCents, 1, 0);
        }
        platformMetricsMapper.upsertIncrement(metricDate, 0, 0, 1, priceCents, 0L);
        financeMetricsMapper.upsertIncrement(metricDate, priceCents, 0L, priceCents, 1, 0);

        if (courseId != null) {
            courseEngagementStatsMapper.upsertCourseStats(
                    courseId,
                    (courseTitle != null) ? courseTitle : "课程",
                    (teacherId != null) ? teacherId : "teacher_system",
                    1,
                    0
            );
        }
        log.info("Recorded Enrollment: courseId={}, teacherId={}, priceCents={}, date={}", courseId, teacherId, priceCents, metricDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPayment(long amountCents, LocalDate date) {
        LocalDate metricDate = (date != null) ? date : LocalDate.now();
        platformMetricsMapper.upsertIncrement(metricDate, 0, 0, 1, amountCents, 0L);
        financeMetricsMapper.upsertIncrement(metricDate, amountCents, 0L, amountCents, 1, 0);
        log.info("Recorded Payment: amountCents={}, date={}", amountCents, metricDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordRefund(long refundCents, LocalDate date) {
        LocalDate metricDate = (date != null) ? date : LocalDate.now();
        platformMetricsMapper.upsertIncrement(metricDate, 0, 0, 0, 0L, refundCents);
        financeMetricsMapper.upsertIncrement(metricDate, 0L, refundCents, -refundCents, 0, 1);
        log.info("Recorded Refund: refundCents={}, date={}", refundCents, metricDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordCourseProgress(String courseId, String teacherId, boolean completed, LocalDate date) {
        LocalDate metricDate = (date != null) ? date : LocalDate.now();
        int completedDelta = completed ? 1 : 0;
        if (teacherId != null) {
            teacherMetricsMapper.upsertIncrement(teacherId, metricDate, 0, 0L, 1, completedDelta);
        }
        if (courseId != null) {
            courseEngagementStatsMapper.upsertCourseStats(
                    courseId,
                    "课程",
                    (teacherId != null) ? teacherId : "teacher_system",
                    0,
                    completedDelta
            );
        }
        log.info("Recorded CourseProgress: courseId={}, teacherId={}, completed={}, date={}", courseId, teacherId, completed, metricDate);
    }
}
