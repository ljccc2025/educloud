package com.educloud.user.service;

import com.educloud.user.config.SessionProperties;
import com.educloud.user.mapper.RefreshSessionMapper;
import com.educloud.user.observability.UserMetrics;
import com.educloud.user.session.SessionStore;
import com.educloud.user.support.AuditWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 会话撤销（DB 权威 + Redis 读模型）。BUG-037 修复：Redis 读模型写入失败被捕获
 * 并告警，不再回滚 DB 权威撤销；refresh 以 DB 权威行为准，Redis 残留 ACTIVE
 * 标记最多随 access-token TTL 自然过期（设计规格第 4.4 节）。
 */
@Service
public class SessionRevocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionRevocationService.class);

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
        // BUG-037 修复：原先 Redis 写入与 DB 撤销同事务，Redis 故障时整个事务回滚，
        // DB 撤销一并丢失，被盗令牌在 Redis 恢复后仍可持续续期（refresh 自愈
        // 重建 ACTIVE 标记）。现在捕获 Redis 异常：DB 权威撤销照常提交，refresh
        // 会被 DB 的 REVOKED 状态拒绝；Redis 残留 ACTIVE 标记随 access-token
        // TTL（分钟级）自然过期，仅影响已签发短命令牌的即时失效性，不影响撤销语义。
        try {
            sessionStore.markRevoked(familyId, sessionProperties.accessTokenTtl());
        } catch (Exception ex) {
            LOGGER.error("Failed to mark session revoked in Redis read model; "
                            + "DB revocation committed, Redis marker will expire by TTL. "
                            + "familyId={}, reason={}",
                    familyId, reason, ex);
        }
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
