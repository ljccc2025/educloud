package com.educloud.user.support;

import com.educloud.user.entity.AuditEventEntity;
import com.educloud.user.mapper.AuditEventMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 审计事实写入（audit_event 只追加；应用账号仅 INSERT/SELECT）。
 * 依据：数据设计第 14 节、可靠性设计第 9 节。
 */
@Component
public final class AuditWriter {

    /** 与 audit_event.user_agent VARCHAR(512) 对齐；超长 UA 截断入库，防止认证接口被单请求打挂。 */
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final AuditEventMapper auditEventMapper;

    public AuditWriter(AuditEventMapper auditEventMapper) {
        this.auditEventMapper = Objects.requireNonNull(auditEventMapper, "auditEventMapper");
    }

    public void write(AuditEntry entry) {
        AuditEventEntity audit = new AuditEventEntity();
        audit.setAuditId(UUID.randomUUID().toString());
        audit.setActorType(entry.actorType());
        audit.setActorId(entry.actorId());
        audit.setActorRolesJson(entry.actorRolesJson());
        audit.setAction(entry.action());
        audit.setResourceType(entry.resourceType());
        audit.setResourceId(entry.resourceId());
        audit.setResult(entry.result());
        audit.setReason(entry.reason());
        audit.setBeforeSummaryJson(entry.beforeSummaryJson());
        audit.setAfterSummaryJson(entry.afterSummaryJson());
        audit.setIp(entry.ip());
        audit.setUserAgent(safeUserAgent(entry.userAgent()));
        audit.setRequestId(entry.requestId() == null ? "unavailable" : entry.requestId());
        audit.setTraceId(entry.traceId());
        audit.setOccurredAt(Instant.now());
        audit.setRetentionClass(entry.retentionClass());
        auditEventMapper.insert(audit);
    }

    public static String safeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT_LENGTH
                ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }

    public record AuditEntry(
            String actorType,
            String actorId,
            String actorRolesJson,
            String action,
            String resourceType,
            String resourceId,
            String result,
            String reason,
            String beforeSummaryJson,
            String afterSummaryJson,
            String ip,
            String userAgent,
            String requestId,
            String traceId,
            String retentionClass) {

        public AuditEntry {
            Objects.requireNonNull(actorType, "actorType");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(result, "result");
        }
    }
}
