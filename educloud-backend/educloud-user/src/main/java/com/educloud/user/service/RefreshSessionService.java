package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.dto.response.LoginResponse;
import com.educloud.user.dto.response.UserSummary;
import com.educloud.user.entity.RefreshSessionEntity;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.RefreshSessionMapper;
import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.security.ClaimsFactory;
import com.educloud.user.security.UserJwtEncoder;
import com.educloud.user.session.SessionFactory;
import com.educloud.user.observability.UserMetrics;
import com.educloud.user.session.SessionStore;
import com.educloud.user.support.AuditWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh Token 原子轮换。依据：M03 设计规格第 4.3 节（行锁、ACTIVE→ROTATED 原子迁移、
 * 并发宽限窗口、窗口外重用撤销家族、指纹不一致撤销、用户状态/tokenVersion 校验）。
 */
@Service
public class RefreshSessionService {

    private final RefreshSessionMapper refreshSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SessionFactory sessionFactory;
    private final SessionStore sessionStore;
    private final SessionRevocationService revocationService;
    private final ClaimsFactory claimsFactory;
    private final UserJwtEncoder jwtEncoder;
    private final SessionProperties sessionProperties;
    private final UserMetrics userMetrics;
    private final Clock clock;

    public RefreshSessionService(
            RefreshSessionMapper refreshSessionMapper,
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            SysRoleMapper sysRoleMapper,
            SysPermissionMapper sysPermissionMapper,
            SessionFactory sessionFactory,
            SessionStore sessionStore,
            SessionRevocationService revocationService,
            ClaimsFactory claimsFactory,
            UserJwtEncoder jwtEncoder,
            SessionProperties sessionProperties,
            UserMetrics userMetrics,
            Clock clock) {
        this.refreshSessionMapper = Objects.requireNonNull(refreshSessionMapper, "refreshSessionMapper");
        this.sysUserMapper = Objects.requireNonNull(sysUserMapper, "sysUserMapper");
        this.userProfileMapper = Objects.requireNonNull(userProfileMapper, "userProfileMapper");
        this.sysRoleMapper = Objects.requireNonNull(sysRoleMapper, "sysRoleMapper");
        this.sysPermissionMapper = Objects.requireNonNull(sysPermissionMapper, "sysPermissionMapper");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.revocationService = Objects.requireNonNull(revocationService, "revocationService");
        this.claimsFactory = Objects.requireNonNull(claimsFactory, "claimsFactory");
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder");
        this.sessionProperties = Objects.requireNonNull(sessionProperties, "sessionProperties");
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public RefreshResult refresh(String rawToken, String clientFingerprintHash, String requestId) {
        String tokenHash = SessionFactory.sha256Hex(rawToken);
        RefreshSessionEntity row = refreshSessionMapper.selectByTokenHashForUpdate(tokenHash);
        if (row == null) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
        if ("REVOKED".equals(row.getStatus())) {
            throw new BusinessException(UserErrorCode.SESSION_REVOKED);
        }
        Instant now = clock.instant();
        if ("EXPIRED".equals(row.getStatus()) || row.getExpiresAt().isBefore(now)) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
        if ("ROTATED".equals(row.getStatus())) {
            Instant consumed = row.getConsumedAt() == null ? row.getIssuedAt() : row.getConsumedAt();
            boolean withinGrace = !now.isBefore(consumed)
                    && Duration.between(consumed, now).compareTo(sessionProperties.rotationGraceWindow()) <= 0;
            if (withinGrace) {
                // 并发宽限窗口内旧 Token 重用：稳定冲突，不撤销（设计规格第 4.3 节）。
                throw new BusinessException(UserErrorCode.REFRESH_ALREADY_ROTATED);
            }
            revocationService.revokeFamily(row.getFamilyId(), "SESSION_REUSE_DETECTED", requestId);
            userMetrics.sessionReuseDetected();
            throw new BusinessException(UserErrorCode.SESSION_REUSE_DETECTED);
        }
        if (!row.getClientFingerprintHash().equals(clientFingerprintHash)) {
            revocationService.revokeFamily(row.getFamilyId(), "CLIENT_FINGERPRINT_MISMATCH", requestId);
            userMetrics.sessionReuseDetected();
            throw new BusinessException(UserErrorCode.SESSION_REUSE_DETECTED);
        }

        SysUserEntity user = sysUserMapper.selectById(row.getUserId());
        if (user == null || "DISABLED".equals(user.getStatus())) {
            throw new BusinessException(UserErrorCode.ACCOUNT_DISABLED);
        }
        java.util.Optional<SessionStore.SessionSnapshot> snapshotOpt = sessionStore.read(row.getFamilyId());
        if (snapshotOpt.isPresent()) {
            SessionStore.SessionSnapshot snapshot = snapshotOpt.get();
            if (!"ACTIVE".equals(snapshot.status())
                    || snapshot.tokenVersion() != user.getTokenVersion()) {
                throw new BusinessException(UserErrorCode.SESSION_REVOKED);
            }
        } else {
            // Redis 读模型缺失（标记 TTL 到期/清库）而 DB 权威行仍 ACTIVE：
            // 自愈重建读模型，保证 7 天 Refresh 生命周期不被 15 分钟标记 TTL 打断。
            sessionStore.writeActive(
                    row.getFamilyId(),
                    String.valueOf(user.getId()),
                    user.getTokenVersion(),
                    sessionProperties.refreshTokenTtl());
        }

        int rotated = refreshSessionMapper.markRotated(row.getId(), now);
        if (rotated != 1) {
            throw new BusinessException(UserErrorCode.REFRESH_ALREADY_ROTATED);
        }

        SessionFactory.SessionCreated created = sessionFactory.create(
                user.getId(), row.getClientType(), clientFingerprintHash);
        RefreshSessionEntity child = new RefreshSessionEntity();
        child.setFamilyId(row.getFamilyId());
        child.setTokenId(UUID.randomUUID().toString());
        child.setParentTokenId(row.getTokenId());
        child.setUserId(user.getId());
        child.setSessionTokenHash(created.tokenHash());
        child.setStatus("ACTIVE");
        child.setClientType(row.getClientType());
        child.setClientFingerprintHash(clientFingerprintHash);
        child.setIssuedAt(now);
        child.setExpiresAt(now.plus(sessionProperties.refreshTokenTtl()));
        refreshSessionMapper.insert(child);
        userMetrics.refreshRotated();

        sessionStore.writeActive(
                row.getFamilyId(),
                String.valueOf(user.getId()),
                user.getTokenVersion(),
                sessionProperties.accessTokenTtl());

        List<String> roles = sysRoleMapper.selectCodesByUserId(user.getId());
        List<String> permissions = sysPermissionMapper.selectCodesByUserId(user.getId());
        String accessToken = jwtEncoder.encode(claimsFactory.userClaims(
                String.valueOf(user.getId()),
                row.getFamilyId(),
                user.getUserType(),
                user.getTokenVersion(),
                roles,
                permissions,
                now,
                sessionProperties.accessTokenTtl()));

        UserProfileEntity profile = userProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserProfileEntity>()
                        .eq("user_id", user.getId()));
        String displayName = profile == null ? user.getUsername() : profile.getDisplayName();

        return new RefreshResult(
                created.rawToken(),
                new LoginResponse(
                        accessToken,
                        sessionProperties.accessTokenTtl().getSeconds(),
                        new UserSummary(
                                String.valueOf(user.getId()),
                                user.getUsername(),
                                displayName,
                                user.getUserType(),
                                roles,
                                permissions,
                                null)));
    }

    public record RefreshResult(String refreshTokenRaw, LoginResponse response) {
    }
}
