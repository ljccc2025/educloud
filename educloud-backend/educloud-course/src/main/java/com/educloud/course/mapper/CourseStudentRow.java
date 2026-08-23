package com.educloud.course.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师学生列表投影行（CourseEnrollmentMapper.selectStudentPage，M05 任务 13）。
 *
 * <p>course_enrollment JOIN course（过滤该课程，ACTIVE）按 enrolled_at 倒序分页；
 * MyBatis map-underscore-to-camel-case 自动映射。displayName 不在本查询（M05 无
 * user Profile 客户端，DTO 恒 null，javadoc 已说明）。</p>
 */
@Data
public class CourseStudentRow {

    private Long studentId;

    private LocalDateTime enrolledAt;
}
