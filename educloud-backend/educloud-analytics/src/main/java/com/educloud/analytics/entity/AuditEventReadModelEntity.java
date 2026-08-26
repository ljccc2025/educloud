package com.educloud.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("audit_event_read_model")
public class AuditEventReadModelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("audit_id")
    private String auditId;

    @TableField("source_service")
    private String sourceService;

    @TableField("actor_id")
    private String actorId;

    @TableField("actor_roles")
    private String actorRoles;

    @TableField("action")
    private String action;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private String resourceId;

    @TableField("level")
    private String level;

    @TableField("client_ip")
    private String clientIp;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
