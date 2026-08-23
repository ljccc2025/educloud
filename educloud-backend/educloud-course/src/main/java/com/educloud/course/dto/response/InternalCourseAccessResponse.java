package com.educloud.course.dto.response;

import java.util.List;

/**
 * 内部课程访问快照（M05 任务 17）：GET /internal/v1/courses/{id}，供 M06 content
 * 消费课程归属/可见性信息。
 *
 * <p>依据：M05 设计规格 §9 —— 教师课程归属通过受认证的 Course 内部权限查询确认，
 * 返回 teachers（teacherId + teacherRole）快照；M06 可据此确认教师是否归属该课程。
 * Snowflake ID 一律 String（M04 坑 1：前端禁止 Number()）。contentReady 为恒 false
 * 占位（M06 激活 course_content_readiness_projection 前不参与可见性判断，见
 * CourseService.republish 的 M05 就绪 gate 恒放行说明）。</p>
 */
public record InternalCourseAccessResponse(
        String courseId,
        String lifecycleStatus,
        String publishedVersionId,
        String draftVersionId,
        String ownerTeacherId,
        boolean contentReady,
        List<InternalTeacherRef> teachers) {

    /** 授课教师快照：teacherId（String）+ teacherRole（OWNER/CO_TEACHER）。 */
    public record InternalTeacherRef(String teacherId, String teacherRole) {
    }
}
