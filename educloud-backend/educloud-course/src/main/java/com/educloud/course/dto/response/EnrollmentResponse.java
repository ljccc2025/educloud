package com.educloud.course.dto.response;

import java.time.LocalDateTime;

/**
 * 选课响应（M05 任务 13）：POST /courses/{id}/enrollments。
 *
 * <p>Snowflake id 一律 String（M04 坑 1）；新建与幂等重选（已存在返回现状）共用同一
 * 结构。source=FREE/ORDER、status=ACTIVE/REVOKED（M05 无撤销触发路径，恒 FREE/ACTIVE）。</p>
 */
public record EnrollmentResponse(
        String enrollmentId,
        String courseId,
        String studentId,
        String source,
        String status,
        LocalDateTime enrolledAt) {
}
