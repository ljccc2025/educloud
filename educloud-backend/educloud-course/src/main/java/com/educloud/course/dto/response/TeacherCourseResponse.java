package com.educloud.course.dto.response;

/**
 * 教师课程管理列表项（M05 任务 22）：GET /api/v1/teacher/courses。
 *
 * <p>返回当前归属教师（course_teacher 行）的全部课程含生命周期/版本状态：编辑入口由
 * versionStatus/lifecycleStatus 驱动（DRAFT 可编辑、PENDING_REVIEW 只读、REJECTED 可
 * 复制新草稿、PUBLISHED/OFFLINE/ARCHIVED 可从发布版本创建新草稿）。Snowflake ID 一律
 * String（M04 坑 1）；price 金额字符串化（十进制金额，与 CourseDraftResponse 同风格）；
 * coverUrl 由 FileClient 批量 grant 组装（subject=USER 教师本人），无封面/不可达时 null。</p>
 */
public record TeacherCourseResponse(
        String courseId,
        String versionId,
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
