package com.educloud.course.observability;

import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.entity.AuditEventEntity;
import com.educloud.course.mapper.AuditEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Course 审计事实写入器（audit_event 只追加；应用账号仅 INSERT/SELECT）。
 *
 * <p>M05 任务 16 + 审查修复：关键写操作（建课/提交审核/审批/驳回/下架/重上架/归档/选课/
 * 评价隐藏/撤回）在业务事务内追加审计行。actor_type 存角色名（{@link #actorType(Set)}
 * 按特权优先级 ADMIN>REVIEWER>TEACHER>STUDENT 取最高角色；多角色取高特权，未知角色
 * 回退字典序首个，空角色回退 USER）或回退 USER；与 audit_event.actor_type VARCHAR(32)
 * 对齐，超长截断（{@link #safeActorType(String)}）。request_id/trace_id 由
 * {@link RequestContextAccessor} 解析；ip 仅在存在 Servlet 请求上下文时记录（无上下文
 * 为 NULL，与 FileAccessAuditWriter 同语义）。write 入口对 action/resourceType/result/
 * actorId 做 {@link Objects#requireNonNull} 硬校验，防止审计事实失真。</p>
 */
@Component
public class AuditWriter {

    /** 与 audit_event.actor_type VARCHAR(32) 对齐。 */
    private static final int MAX_ACTOR_TYPE_LENGTH = 32;
    /** 与 audit_event.reason VARCHAR(512) 对齐。 */
    private static final int MAX_REASON_LENGTH = 512;
    private static final String ACTOR_TYPE_FALLBACK = "USER";
    private static final String RETENTION_CLASS = "standard";
    /** actor_type 特权优先级：ADMIN（SUPER_ADMIN/SYSTEM_ADMIN）> REVIEWER（COURSE_REVIEWER）> TEACHER > STUDENT。 */
    private static final java.util.List<String> ROLE_PRIVILEGE_ORDER = java.util.List.of(
            "SUPER_ADMIN", "SYSTEM_ADMIN", "COURSE_REVIEWER", "TEACHER", "STUDENT");

    private final AuditEventMapper auditEventMapper;
    private final RequestContextAccessor requestContextAccessor;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditWriter(
            AuditEventMapper auditEventMapper,
            RequestContextAccessor requestContextAccessor,
            ObjectMapper objectMapper,
            Clock clock) {
        this.auditEventMapper = Objects.requireNonNull(auditEventMapper, "auditEventMapper");
        this.requestContextAccessor = Objects.requireNonNull(requestContextAccessor, "requestContextAccessor");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 写一条 SUCCESS 审计事实（关键写操作完成后同事务调用）。
     *
     * @param action       操作名（如 AUDIT_APPROVED、ENROLLMENT_CREATED）
     * @param resourceType 资源类型（如 course、course_audit、enrollment、course_review）
     * @param resourceId   资源主键字符串
     * @param actorId      操作者 userId
     * @param roles        操作者 JWT roles（空 → actor_type=USER）
     * @param result       结果（SUCCESS/FAILURE）
     * @param reason       补充说明（驳回原因等，可为 null）
     */
    public void write(
            String action,
            String resourceType,
            String resourceId,
            Long actorId,
            Set<String> roles,
            String result,
            String reason) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(result, "result");
        AuditEventEntity audit = new AuditEventEntity();
        audit.setAuditId(UUID.randomUUID().toString());
        audit.setActorType(safeActorType(actorType(roles)));
        audit.setActorId(actorId == null ? null : String.valueOf(actorId));
        audit.setActorRolesJson(rolesJson(roles));
        audit.setAction(action);
        audit.setResourceType(resourceType);
        audit.setResourceId(resourceId);
        audit.setResult(result);
        audit.setReason(safeReason(reason));
        audit.setIp(clientIp());
        audit.setUserAgent(null);
        String requestId = requestContextAccessor.requestId();
        audit.setRequestId(requestId == null ? "unavailable" : requestId);
        java.util.Optional<String> traceId = requestContextAccessor.traceId();
        audit.setTraceId(traceId == null ? null : traceId.orElse(null));
        audit.setOccurredAt(clock.instant());
        audit.setRetentionClass(RETENTION_CLASS);
        auditEventMapper.insert(audit);
    }

    /** 角色集合 → actor_type：特权优先级 ADMIN>REVIEWER>TEACHER>STUDENT（多角色取最高
     *  特权）；未知角色取字典序首个保证确定性；空/缺失 → USER。 */
    public static String actorType(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return ACTOR_TYPE_FALLBACK;
        }
        for (String privileged : ROLE_PRIVILEGE_ORDER) {
            if (roles.contains(privileged)) {
                return privileged;
            }
        }
        return roles.stream().sorted().findFirst().orElse(ACTOR_TYPE_FALLBACK);
    }

    /** actor_type 与 VARCHAR(32) 对齐：超长截断，null → USER。 */
    public static String safeActorType(String actorType) {
        if (actorType == null) {
            return ACTOR_TYPE_FALLBACK;
        }
        return actorType.length() <= MAX_ACTOR_TYPE_LENGTH
                ? actorType
                : actorType.substring(0, MAX_ACTOR_TYPE_LENGTH);
    }

    private static String safeReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_REASON_LENGTH);
    }

    private String rolesJson(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(roles.stream().sorted().collect(Collectors.toList()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("failed to serialize audit actor roles", failure);
        }
    }

    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            if (request != null) {
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        }
        return null;
    }
}
