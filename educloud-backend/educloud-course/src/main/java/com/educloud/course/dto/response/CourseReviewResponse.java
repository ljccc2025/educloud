package com.educloud.course.dto.response;

import java.time.LocalDateTime;

/**
 * 课程评价响应（M05 任务 14）：POST /courses/{id}/reviews upsert 结果、
 * DELETE /course-reviews/{id} 隐藏结果、课程详情 reviews 列表元素。
 *
 * <p>Snowflake id/studentId 为 String（规格 §6：DTO 一律 String，M04 坑 1）；
 * status 下发 VISIBLE/HIDDEN（详情列表只查询 VISIBLE，隐藏行仅管理端可见）。
 * 隐藏保留审计（软隐藏），不物理删除。</p>
 */
public record CourseReviewResponse(
        String id,
        String studentId,
        Integer rating,
        String content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
