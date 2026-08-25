package com.educloud.course.dto.response;

/**
 * 内部选课状态响应：GET /internal/v1/courses/{courseId}/enrollments/{studentId}。
 *
 * <p>供 M06 content 下载准入校验消费（BUG-002 修复：付费/免费课程的学员权益
 * 权威来源均为 course_enrollment——免费课程走 enroll 报名、付费课程由
 * OrderPaidListener 开课落行）。雪花 ID 一律字符串化；无选课记录时 status 为
 * null、enrolled=false（200 而非 404——「未选课」是正常业务状态而非错误）。</p>
 */
public record InternalEnrollmentStatusResponse(
        String courseId,
        String studentId,
        String status,
        boolean enrolled) {
}
