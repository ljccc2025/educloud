package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 审计事实（audit_event，只追加；应用账号仅 INSERT/SELECT —— 见
 * deploy/sql/course/V000__technical_tables.sql）。
 *
 * <p>M05 任务 16：与 educloud-user 的 AuditEventEntity 同构；actor_type VARCHAR(32)
 * 由 {@link com.educloud.course.observability.AuditWriter} 截断保证兼容。</p>
 */
@Data
@TableName("audit_event")
public class AuditEventEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String auditId;

    private String actorType;

    private String actorId;

    private String actorRolesJson;

    private String action;

    private String resourceType;

    private String resourceId;

    private String result;

    private String reason;

    private String beforeSummaryJson;

    private String afterSummaryJson;

    private String ip;

    private String userAgent;

    private String requestId;

    private String traceId;

    private Instant occurredAt;

    private String retentionClass;
}
