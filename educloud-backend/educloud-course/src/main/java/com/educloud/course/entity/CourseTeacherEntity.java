package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授课教师（course_teacher）：负责人 + 共同授课；(course_id, teacher_id) 唯一。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course_teacher")
public class CourseTeacherEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseId;

    private Long teacherId;

    /** OWNER/CO_TEACHER。 */
    private String teacherRole;

    private LocalDateTime joinedAt;
}
