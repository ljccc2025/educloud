package com.educloud.course.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 课程详情响应（M05 任务 11）：GET /api/v1/courses/{id}。
 *
 * <p>可见性：PUBLISHED 公开；DRAFT/PENDING_REVIEW/OFFLINE 仅归属教师（lifecycleStatus
 * 下发 OWNER 视角状态）；ARCHIVED 与越权请求一律 404 COURSE_NOT_FOUND（规格 §6）。
 * coverUrl 恒 null（任务 12 接 File grant）；reviews 本任务恒空列表（任务 14 接评价，
 * 届时替换占位 Review 结构）。教师展示名解析不在 M05 范围，teachers 只下发
 * teacherId+role（负责人+共同授课）。</p>
 */
public record CourseDetailResponse(
        String id,
        String title,
        String subtitle,
        String description,
        String coverUrl,
        String level,
        String price,
        String currency,
        String categoryId,
        String categoryName,
        List<Teacher> teachers,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Integer enrollmentCount,
        boolean enrolled,
        String lifecycleStatus,
        List<Review> reviews) {

    /** 教师成员：teacherId（String）+ role（OWNER/CO_TEACHER）。 */
    public record Teacher(String teacherId, String teacherRole) {
    }

    /** 评价占位（任务 14 以 CourseReviewResponse 替换）：本任务恒空列表。 */
    public record Review(String reviewId, String studentName, Integer rating,
                         String content, String createdAt) {
    }
}
