package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_revision")
public class ContentRevisionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseContentId;

    private Long courseId;

    private Integer revisionNo;

    /**
     * DRAFT, PENDING_REVIEW, PUBLISHED, SUPERSEDED, REJECTED, WITHDRAWN
     */
    private String revisionStatus;

    private String contentHash;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime submittedAt;

    private LocalDateTime publishedAt;
}
