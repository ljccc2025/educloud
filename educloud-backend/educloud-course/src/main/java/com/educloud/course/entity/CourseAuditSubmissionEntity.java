package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核提交（course_audit_submission）：course_version_id 唯一（一个版本至多一条提交）；驳回时 reason 必填。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course_audit_submission")
public class CourseAuditSubmissionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseId;

    /** 被审核的课程版本；uk_course_audit_submission_version 保证一版本至多一条提交。 */
    private Long courseVersionId;

    /** PENDING/APPROVED/REJECTED/WITHDRAWN。 */
    private String status;

    private Long submittedBy;

    private LocalDateTime submittedAt;

    private LocalDateTime withdrawnAt;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    /** 驳回时必填。 */
    private String reason;
}
