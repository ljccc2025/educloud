package com.educloud.course.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 课程详情响应（M05 任务 11，任务 14 接评价）：GET /api/v1/courses/{id}。
 *
 * <p>可见性：PUBLISHED 公开；DRAFT/PENDING_REVIEW/OFFLINE 仅归属教师（lifecycleStatus
 * 下发 OWNER 视角状态）；ARCHIVED 与越权请求一律 404 COURSE_NOT_FOUND（规格 §6）。
 * coverUrl 由 File grant 组装（任务 12）；reviews 为可见评价列表（任务 14：VISIBLE
 * 评价分页第一页，updatedAt 倒序；规格 §6「可见评价列表，分页」，隐藏评价不出现）。</p>
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
        List<CourseReviewResponse> reviews) {

    /** 教师成员：teacherId（String）+ role（OWNER/CO_TEACHER）。 */
    public record Teacher(String teacherId, String teacherRole) {
    }
}
