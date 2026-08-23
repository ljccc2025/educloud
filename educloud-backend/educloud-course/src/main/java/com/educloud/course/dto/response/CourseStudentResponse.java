package com.educloud.course.dto.response;

import java.time.LocalDateTime;

/**
 * 教师学生列表项（M05 任务 13）：GET /courses/{id}/students。
 *
 * <p>本任务返回 studentId + enrolledAt；displayName 恒 null —— M05 无 user Profile
 * 客户端，学生展示名解析留给后续接入（javadoc 显式说明，避免前端误判字段缺失）。</p>
 */
public record CourseStudentResponse(
        String studentId,
        String displayName,
        LocalDateTime enrolledAt) {
}
