package com.educloud.course.dto.response;

import java.time.LocalDateTime;

/**
 * 我的课程列表项（M05 任务 13）：GET /me/enrollments。
 *
 * <p>课程信息（id/title/coverUrl）+ 选课状态（status/enrolledAt）；coverUrl 由
 * FileClient 按页批量 grant（subject=USER 当前学生）；学习进度归 Content 服务
 * （M06 接入），Course 不复制进度权威事实。</p>
 */
public record MyCourseResponse(
        String courseId,
        String title,
        String coverUrl,
        String status,
        LocalDateTime enrolledAt) {
}
