package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 选课（course_enrollment）：(course_id, student_id) 唯一（幂等）；version 行内递增（Enrollment 聚合乐观锁）。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course_enrollment")
public class CourseEnrollmentEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseId;

    private Long studentId;

    /** FREE/ORDER。 */
    private String source;

    private Long sourceOrderId;

    /** ACTIVE/REVOKED。 */
    private String status;

    private LocalDateTime enrolledAt;

    private LocalDateTime accessEndedAt;

    private String revokeReason;

    /** 乐观锁版本：Enrollment 聚合行内递增，由拦截器自动回写，禁止手动 +1。 */
    @Version
    private Long version;
}
