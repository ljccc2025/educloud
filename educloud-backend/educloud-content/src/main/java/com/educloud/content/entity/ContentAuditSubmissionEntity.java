package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_audit_submission")
public class ContentAuditSubmissionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseId;

    private Long contentRevisionId;

    private Integer revisionNo;

    private String snapshotJson;

    /**
     * PENDING, APPROVED, REJECTED, WITHDRAWN
     */
    private String status;

    private Long submittedBy;

    private Long reviewedBy;

    private String rejectReason;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private LocalDateTime withdrawnAt;
}
