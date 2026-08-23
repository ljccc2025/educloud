package com.educloud.course.dto.response;

/**
 * 管理端课程管理列表项（M05 任务 23）：GET /api/v1/admin/courses（course:audit）。
 *
 * <p>管理查询缺口确认（任务 23 关键上下文）：公开目录 GET /courses 仅 PUBLISHED 且
 * CourseListQuery 无 status 参数（网关 PUBLIC_READ 匿名放行，不能承载管理全状态查询），
 * 故按任务 22 先例补齐管理专用端点。返回全部生命周期（DRAFT/PENDING_REVIEW/PUBLISHED/
 * OFFLINE/ARCHIVED）的课程分页；当前工作版本由 COALESCE(draft_version_id,
 * published_version_id, 最新版本) 驱动（与 TeacherCourseResponse 同投影）。Snowflake ID
 * 一律 String（M04 坑 1）；price 金额字符串化；coverUrl 由 FileClient 批量 grant 组装
 * （subject=USER 当前管理员），无封面/不可达时 null。</p>
 */
public record AdminCourseResponse(
        String courseId,
        String versionId,
        Integer versionNo,
        String title,
        String coverUrl,
        String level,
        String price,
        String currency,
        String categoryId,
        String versionStatus,
        String lifecycleStatus,
        Integer enrollmentCount) {
}
