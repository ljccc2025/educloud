package com.educloud.user.service;

import com.educloud.user.config.SessionProperties;
import com.educloud.user.mapper.RefreshSessionMapper;
import com.educloud.user.observability.UserMetrics;
import com.educloud.user.session.SessionStore;
import com.educloud.user.support.AuditWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 会话撤销（DB 权威 + Redis 读模型；撤销写 Redis 失败由调用方重试/补偿，设计规格第 4.4 节）。
 */
@Service
public class SessionRevocationService {

    private final RefreshSessionMapper refreshSessionMapper;
    private final SessionStore sessionStore;
    private final SessionProperties sessionProperties;
    private final Clock clock;
    private final AuditWriter auditWriter;
    private final UserMetrics userMetrics;

    public SessionRevocationService(
            RefreshSessionMapper refreshSessionMapper,
            SessionStore sessionStore,
            SessionProperties sessionProperties,
            Clock clock,
            AuditWriter auditWriter,
            UserMetrics userMetrics) {
        this.refreshSessionMapper = Objects.requireNonNull(refreshSessionMapper, "refreshSessionMapper");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sessionProperties = Objects.requireNonNull(sessionProperties, "sessionProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
    }

    @Transactional
    public void revokeFamily(String familyId, String reason, String requestId) {
        Instant now = clock.instant();
        refreshSessionMapper.revokeFamily(familyId, now, reason);
        sessionStore.markRevoked(familyId, sessionProperties.accessTokenTtl());
        userMetrics.sessionRevoked();
        auditWriter.write(new AuditWriter.AuditEntry(
                "SYSTEM",
                "unknown",
                null,
                "SESSION_REVOKED",
                "refresh_session",
                familyId,
                "SUCCESS",
                reason,
                null,
                null,
                null,
                null,
                requestId,
                null,
                "AUTH"));
    }

    /** 按 Refresh Token 明文定位 family 并撤销（注销端点用；未知 Token 幂等返回）。 */
    @Transactional
    public void revokeFamilyByToken(String rawToken, String reason, String requestId) {
        String tokenHash = com.educloud.user.session.SessionFactory.sha256Hex(rawToken);
        var row = refreshSessionMapper.selectByTokenHash(tokenHash);
        if (row != null) {
            revokeFamily(row.getFamilyId(), reason, requestId);
        }
    }

    @Transactional
    public void revokeAllForUserExcept(Long userId, String exceptFamilyId, String reason, String requestId) {
        List<String> families = refreshSessionMapper.selectActiveFamilyIdsByUserId(userId);
        for (String familyId : families) {
            if (!familyId.equals(exceptFamilyId)) {
                revokeFamily(familyId, reason, requestId);
            }
        }
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason, String requestId) {
        List<String> families = refreshSessionMapper.selectActiveFamilyIdsByUserId(userId);
        for (String familyId : families) {
            revokeFamily(familyId, reason, requestId);
        }
    }
}
