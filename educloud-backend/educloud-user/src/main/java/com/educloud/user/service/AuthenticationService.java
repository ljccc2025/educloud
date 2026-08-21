package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.config.LoginProperties;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.dto.request.LoginRequest;
import com.educloud.user.dto.response.LoginResponse;
import com.educloud.user.dto.response.UserSummary;
import com.educloud.user.entity.LoginAuditEntity;
import com.educloud.user.entity.RefreshSessionEntity;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.LoginAuditMapper;
import com.educloud.user.mapper.RefreshSessionMapper;
import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.security.ClaimsFactory;
import com.educloud.user.security.UserJwtEncoder;
import com.educloud.user.session.SessionFactory;
import com.educloud.user.session.SessionStore;
import com.educloud.user.support.AuditWriter;
import com.educloud.user.support.ClientFingerprint;
import com.educloud.user.support.Masking;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 登录与账号保护。依据：M03 设计规格第 5 节（统一失败语义、失败锁定、审计、Redis 会话写入、
 * Access Token 签发）与第 4.1/4.2 节（Gateway 对齐契约）。
 */
@Service
public final class AuthenticationService {

    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final RefreshSessionMapper refreshSessionMapper;
    private final LoginAuditMapper loginAuditMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionFactory sessionFactory;
    private final SessionStore sessionStore;
    private final ClaimsFactory claimsFactory;
    private final UserJwtEncoder jwtEncoder;
    private final AuditWriter auditWriter;
    private final SessionProperties sessionProperties;
    private final LoginProperties loginProperties;
    private final Clock clock;

    private final String dummyHash;

    public AuthenticationService(
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            SysRoleMapper sysRoleMapper,
            SysPermissionMapper sysPermissionMapper,
            RefreshSessionMapper refreshSessionMapper,
            LoginAuditMapper loginAuditMapper,
            PasswordEncoder passwordEncoder,
            SessionFactory sessionFactory,
            SessionStore sessionStore,
            ClaimsFactory claimsFactory,
            UserJwtEncoder jwtEncoder,
            AuditWriter auditWriter,
            SessionProperties sessionProperties,
            LoginProperties loginProperties,
            Clock clock) {
        this.sysUserMapper = Objects.requireNonNull(sysUserMapper, "sysUserMapper");
        this.userProfileMapper = Objects.requireNonNull(userProfileMapper, "userProfileMapper");
        this.sysRoleMapper = Objects.requireNonNull(sysRoleMapper, "sysRoleMapper");
        this.sysPermissionMapper = Objects.requireNonNull(sysPermissionMapper, "sysPermissionMapper");
        this.refreshSessionMapper = Objects.requireNonNull(refreshSessionMapper, "refreshSessionMapper");
        this.loginAuditMapper = Objects.requireNonNull(loginAuditMapper, "loginAuditMapper");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.claimsFactory = Objects.requireNonNull(claimsFactory, "claimsFactory");
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.sessionProperties = Objects.requireNonNull(sessionProperties, "sessionProperties");
        this.loginProperties = Objects.requireNonNull(loginProperties, "loginProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
        // 账号不存在时也执行一次 BCrypt 比较，避免时序枚举（安全设计第 3.1 节）。
        this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(LoginRequest request, String ip, String userAgent, String requestId) {
        String loginName = request.loginName().trim();
        SysUserEntity user = sysUserMapper.selectByLoginName(loginName);

        boolean matches = user != null
                && passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditFailure(null, Masking.loginName(loginName), "INVALID_CREDENTIALS", ip, userAgent, requestId);
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        if (!matches) {
            registerFailedAttempt(user, ip, userAgent, requestId);
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

        Instant now = clock.instant();
        if ("DISABLED".equals(user.getStatus())) {
            auditFailure(user.getId(), Masking.loginName(loginName), "ACCOUNT_DISABLED", ip, userAgent, requestId);
            throw new BusinessException(UserErrorCode.ACCOUNT_DISABLED);
        }
        if ("LOCKED".equals(user.getStatus())) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                auditFailure(user.getId(), Masking.loginName(loginName), "ACCOUNT_LOCKED", ip, userAgent, requestId);
                throw new BusinessException(UserErrorCode.ACCOUNT_LOCKED);
            }
            // 锁定到期自动放行并重置（设计规格第 5 节）。
            clearLock(user.getId(), now);
        }

        resetFailedAttempts(user.getId(), now);

        List<String> roles = sysRoleMapper.selectCodesByUserId(user.getId());
        List<String> permissions = sysPermissionMapper.selectCodesByUserId(user.getId());

        SessionFactory.SessionCreated created = sessionFactory.create(
                user.getId(), request.portal().name(), ClientFingerprint.of(userAgent));

        RefreshSessionEntity sessionRow = new RefreshSessionEntity();
        sessionRow.setFamilyId(created.familyId());
        sessionRow.setTokenId(java.util.UUID.randomUUID().toString());
        sessionRow.setUserId(user.getId());
        sessionRow.setSessionTokenHash(created.tokenHash());
        sessionRow.setStatus("ACTIVE");
        sessionRow.setClientType(request.portal().name());
        sessionRow.setClientFingerprintHash(created.clientFingerprintHash());
        sessionRow.setIssuedAt(now);
        sessionRow.setExpiresAt(now.plus(sessionProperties.refreshTokenTtl()));
        refreshSessionMapper.insert(sessionRow);

        sessionStore.writeActive(
                created.familyId(),
                String.valueOf(user.getId()),
                user.getTokenVersion(),
                sessionProperties.accessTokenTtl());

        String accessToken = jwtEncoder.encode(claimsFactory.userClaims(
                String.valueOf(user.getId()),
                created.familyId(),
                user.getUserType(),
                user.getTokenVersion(),
                roles,
                permissions,
                now,
                sessionProperties.accessTokenTtl()));

        LoginAuditEntity audit = new LoginAuditEntity();
        audit.setUserId(user.getId());
        audit.setLoginNameMasked(Masking.loginName(loginName));
        audit.setResult("SUCCESS");
        audit.setIp(ip);
        audit.setUserAgent(userAgent);
        audit.setRequestId(requestId);
        audit.setOccurredAt(now);
        loginAuditMapper.insert(audit);

        UserProfileEntity profile = userProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserProfileEntity>()
                        .eq("user_id", user.getId()));
        String displayName = profile == null ? user.getUsername() : profile.getDisplayName();

        return new LoginResult(
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
                                permissions)));
    }

    private void registerFailedAttempt(SysUserEntity user, String ip, String userAgent, String requestId) {
        int count = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        if (count >= loginProperties.maxFailedAttempts()) {
            sysUserMapper.update(null, new UpdateWrapper<SysUserEntity>()
                    .eq("id", user.getId())
                    .set("failed_login_count", count)
                    .set("status", "LOCKED")
                    .set("locked_until", clock.instant().plus(loginProperties.lockDuration()))
                    .set("updated_at", clock.instant()));
        } else {
            sysUserMapper.update(null, new UpdateWrapper<SysUserEntity>()
                    .eq("id", user.getId())
                    .set("failed_login_count", count)
                    .set("updated_at", clock.instant()));
        }
        auditFailure(user.getId(), Masking.loginName(user.getUsername()), "INVALID_CREDENTIALS", ip, userAgent, requestId);
    }

    private void clearLock(Long userId, Instant now) {
        sysUserMapper.update(null, new UpdateWrapper<SysUserEntity>()
                .eq("id", userId)
                .set("locked_until", null)
                .set("updated_at", now));
    }

    private void resetFailedAttempts(Long userId, Instant now) {
        sysUserMapper.update(null, new UpdateWrapper<SysUserEntity>()
                .eq("id", userId)
                .set("failed_login_count", 0)
                .set("last_login_at", now)
                .set("updated_at", now));
    }

    private void auditFailure(Long userId, String maskedLoginName, String failureCode, String ip, String userAgent, String requestId) {
        LoginAuditEntity audit = new LoginAuditEntity();
        audit.setUserId(userId);
        audit.setLoginNameMasked(maskedLoginName);
        audit.setResult("FAILURE");
        audit.setFailureCode(failureCode);
        audit.setIp(ip);
        audit.setUserAgent(userAgent);
        audit.setRequestId(requestId);
        audit.setOccurredAt(clock.instant());
        loginAuditMapper.insert(audit);

        auditWriter.write(new AuditWriter.AuditEntry(
                "USER",
                userId == null ? "unknown" : String.valueOf(userId),
                null,
                "LOGIN_FAILED",
                "user",
                userId == null ? null : String.valueOf(userId),
                "DENIED",
                failureCode,
                null,
                null,
                ip,
                userAgent,
                requestId,
                null,
                "AUTH"));
    }

    public record LoginResult(String refreshTokenRaw, LoginResponse response) {
    }
}
