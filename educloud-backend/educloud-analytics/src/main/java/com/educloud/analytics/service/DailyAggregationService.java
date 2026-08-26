package com.educloud.analytics.service;

import java.time.LocalDate;

public interface DailyAggregationService {

    void recordUserRegistered(LocalDate date);

    void recordCoursePublished(String courseId, String courseTitle, String teacherId, LocalDate date);

    void recordEnrollment(String courseId, String courseTitle, String teacherId, long priceCents, LocalDate date);

    void recordPayment(long amountCents, LocalDate date);

    void recordRefund(long refundCents, LocalDate date);

    void recordCourseProgress(String courseId, String teacherId, boolean completed, LocalDate date);
}
