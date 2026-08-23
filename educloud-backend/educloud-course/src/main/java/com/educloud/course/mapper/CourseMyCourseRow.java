package com.educloud.course.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的课程查询投影行（CourseEnrollmentMapper.selectMyCoursesPage，M05 任务 13）。
 *
 * <p>course_enrollment JOIN course JOIN course_version(published_version_id) 一次取回
 * 课程信息 + 选课状态；cover_file_id 经 FileClient 批量 grant（subject=USER 学生本人）
 * 组装 coverUrl（已选课学生访问自己的已发布课程封面，不签匿名公开 URL）。</p>
 */
@Data
public class CourseMyCourseRow {

    private Long enrollmentId;

    private Long courseId;

    private String title;

    private Long coverFileId;

    private String status;

    private LocalDateTime enrolledAt;
}
