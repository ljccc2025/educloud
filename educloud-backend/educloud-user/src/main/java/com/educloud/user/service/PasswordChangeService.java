package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.session.SessionStore;
import com.educloud.user.support.AuditWriter;
import com.educloud.user.support.PasswordPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 修改密码。依据：M03 设计规格第 4.4 节撤销矩阵（改密：token_version+1、其他 family 撤销、
 * 当前 family 保留 ACTIVE 且 Redis 以新 tokenVersion 重写，旧 Access Token 全部失效、当前会话可刷新续期）。
 */
@Service
public final class PasswordChangeService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionRevocationService revocationService;
    private final SessionStore sessionStore;
    private final SessionProperties sessionProperties;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public PasswordChangeService(
            SysUserMapper sysUserMapper,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SessionRevocationService revocationService,
            SessionStore sessionStore,
            SessionProperties sessionProperties,
            OutboxWriter outboxWriter,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.sysUserMapper = Objects.requireNonNull(sysUserMapper, "sysUserMapper");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        this.revocationService = Objects.requireNonNull(revocationService, "revocationService");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sessionProperties = Objects.requireNonNull(sessionProperties, "sessionProperties");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public void changePassword(
            Long userId,
            String oldPassword,
            String newPassword,
            String currentFamilyId,
            String ip,
            String userAgent,
            String requestId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        passwordPolicy.validate(newPassword);

        long newTokenVersion = user.getTokenVersion() + 1;
        Instant now = Instant.now();
        sysUserMapper.update(null, new UpdateWrapper<SysUserEntity>()
                .eq("id", userId)
                .set("password_hash", passwordEncoder.encode(newPassword))
                .set("token_version", newTokenVersion)
                .set("updated_at", now));

        revocationService.revokeAllForUserExcept(userId, currentFamilyId, "PASSWORD_CHANGED", requestId);
        if (currentFamilyId != null && !currentFamilyId.isBlank()) {
            // 当前会话族保留：Redis 以新 tokenVersion 重写 ACTIVE，旧 Access 立即失效，刷新可续期。
            sessionStore.writeActive(
                    currentFamilyId,
                    String.valueOf(userId),
                    newTokenVersion,
                    sessionProperties.accessTokenTtl());
        }

        auditWriter.write(new AuditWriter.AuditEntry(
                "USER",
                String.valueOf(userId),
                null,
                "PASSWORD_CHANGED",
                "user",
                String.valueOf(userId),
                "SUCCESS",
                null,
                null,
                null,
                ip,
                userAgent,
                requestId,
                null,
                "AUTH"));
    }
}
