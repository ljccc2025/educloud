package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.support.AuditWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户状态管理（锁定/禁用/恢复）。依据：M03 设计规格第 4.4 节撤销矩阵与第 5 节
 * （DISABLED 阻止登录和刷新并撤销活跃会话、token_version+1；LOCKED 阻止新登录；
 * 恢复后账号可登录）。状态迁移带 version 乐观锁，冲突返回 409。
 */
@Service
public class UserStatusService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED");

    private final SysUserMapper sysUserMapper;
    private final SessionRevocationService revocationService;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public UserStatusService(
            SysUserMapper sysUserMapper,
            SessionRevocationService revocationService,
            OutboxWriter outboxWriter,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.sysUserMapper = Objects.requireNonNull(sysUserMapper, "sysUserMapper");
        this.revocationService = Objects.requireNonNull(revocationService, "revocationService");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public void updateStatus(
            Long targetUserId,
            String newStatus,
            Integer version,
            String reason,
            String actorId,
            String ip,
            String userAgent,
            String requestId) {
        if (newStatus == null || !ALLOWED_STATUSES.contains(newStatus)) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_FAILED,
                    "status must be one of ACTIVE, LOCKED, DISABLED");
        }
        SysUserEntity current = sysUserMapper.selectById(targetUserId);
        if (current == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        if (version == null || !version.equals(current.getVersion())) {
            throw new BusinessException(
                    com.educloud.common.error.CommonErrorCode.VERSION_CONFLICT);
        }

        Instant now = Instant.now();
        UpdateWrapper<SysUserEntity> update = new UpdateWrapper<SysUserEntity>()
                .eq("id", targetUserId)
                .eq("version", current.getVersion())
                .set("status", newStatus)
                .set("version", current.getVersion() + 1)
                .set("updated_at", now);
        if ("ACTIVE".equals(newStatus)) {
            // 恢复为 ACTIVE 时重置失败计数与锁定到期，避免"输错一次立即再锁"。
            update.set("failed_login_count", 0).set("locked_until", null);
        }
        int updated = sysUserMapper.update(null, update);
        if (updated != 1) {
            throw new BusinessException(
                    com.educloud.common.error.CommonErrorCode.VERSION_CONFLICT);
        }

        if ("DISABLED".equals(newStatus)) {
            long newTokenVersion = current.getTokenVersion() + 1;
            sysUserMapper.update(null, new UpdateWrapper<SysUserEntity>()
                    .eq("id", targetUserId)
                    .set("token_version", newTokenVersion));
            revocationService.revokeAllForUser(targetUserId, "ACCOUNT_DISABLED", requestId);
            outboxWriter.write(
                    "User",
                    String.valueOf(targetUserId),
                    "UserStatusChanged",
                    1,
                    current.getVersion() + 1L,
                    payload(targetUserId, current.getStatus(), newStatus, reason),
                    requestId,
                    null);
        }

        auditWriter.write(new AuditWriter.AuditEntry(
                "USER",
                actorId == null ? "unknown" : actorId,
                null,
                "USER_STATUS_CHANGED",
                "user",
                String.valueOf(targetUserId),
                "SUCCESS",
                reason,
                null,
                null,
                ip,
                userAgent,
                requestId,
                null,
                "ADMIN"));
    }

    private String payload(Long userId, String fromStatus, String toStatus, String reason) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "userId", String.valueOf(userId),
                    "fromStatus", fromStatus,
                    "toStatus", toStatus,
                    "reason", reason == null ? "" : reason));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize UserStatusChanged payload", exception);
        }
    }
}
