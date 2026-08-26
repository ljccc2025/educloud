package com.educloud.analytics.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    private String auditId;
    private String sourceService;
    private String actorId;
    private String actorRoles;
    private String action;
    private String resourceType;
    private String resourceId;
    private String level;
    private String clientIp;
    private LocalDateTime occurredAt;
    private String payloadJson;
}
