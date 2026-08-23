package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.config.JwtProperties;
import com.educloud.user.config.LoginProperties;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.dto.request.LoginRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录与账号保护单元测试。依据：M03 计划任务 7（统一失败语义/失败锁定/Redis 会话写入/
 * Access Token 签发/审计；失败测试先行）。
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysPermissionMapper sysPermissionMapper;
    @Mock
    private RefreshSessionMapper refreshSessionMapper;
    @Mock
    private LoginAuditMapper loginAuditMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private UserJwtEncoder jwtEncoder;
    @Mock
    private AuditWriter auditWriter;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        SessionProperties sessionProperties = new SessionProperties(
                "test", Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(5), false);
        LoginProperties loginProperties = new LoginProperties(5, Duration.ofMinutes(15));
        JwtProperties jwtProperties = new JwtProperties(
                "/unused", "https://issuer.educloud.local", "educloud-api", Duration.ofMinutes(5));
        service = new AuthenticationService(
                sysUserMapper,
                userProfileMapper,
                sysRoleMapper,
                sysPermissionMapper,
                refreshSessionMapper,
                loginAuditMapper,
                passwordEncoder,
                new SessionFactory(),
                sessionStore,
                new ClaimsFactory(jwtProperties),
                jwtEncoder,
                auditWriter,
                sessionProperties,
                loginProperties,
                org.mockito.Mockito.mock(com.educloud.user.observability.UserMetrics.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LoginRequest request() {
        return new LoginRequest("student01", "password123", LoginRequest.Portal.STUDENT);
    }

    private SysUserEntity activeUser() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setUsername("student01");
        user.setPasswordHash("hashed");
        user.setUserType("STUDENT");
        user.setStatus("ACTIVE");
        user.setTokenVersion(0L);
        user.setFailedLoginCount(0);
        user.setVersion(0);
        return user;
    }

    @Test
    void loginSucceedsWithAccessTokenAndGatewayAlignedRedisSession() {
        SysUserEntity user = activeUser();
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(user);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(sysRoleMapper.selectCodesByUserId(1001L)).thenReturn(List.of("STUDENT"));
        when(sysPermissionMapper.selectCodesByUserId(1001L)).thenReturn(List.of("course:read"));
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(1001L);
        profile.setDisplayName("学生01");
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(jwtEncoder.encode(any())).thenReturn("signed-access-token");

        AuthenticationService.LoginResult result =
                service.login(request(), "127.0.0.1", "Mozilla", "req-1");

        assertThat(result.response().accessToken()).isEqualTo("signed-access-token");
        assertThat(result.response().user().roles()).containsExactly("STUDENT");
        assertThat(result.response().user().permissions()).containsExactly("course:read");
        assertThat(result.refreshTokenRaw()).isNotBlank();

        ArgumentCaptor<RefreshSessionEntity> sessionCaptor =
                ArgumentCaptor.forClass(RefreshSessionEntity.class);
        verify(refreshSessionMapper).insert(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(sessionCaptor.getValue().getSessionTokenHash())
                .isNotEqualTo(result.refreshTokenRaw());

        ArgumentCaptor<String> sidCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(sessionStore).writeActive(
                sidCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq(0L),
                ttlCaptor.capture());
        assertThat(sidCaptor.getValue()).isEqualTo(sessionCaptor.getValue().getFamilyId());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(15));
        verify(loginAuditMapper).insert(any(com.educloud.user.entity.LoginAuditEntity.class));
    }

    @Test
    void unknownUserReturnsSameInvalidCredentialsSemantics() {
        when(sysUserMapper.selectByLoginName("nobody")).thenReturn(null);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.login(
                new LoginRequest("nobody", "whatever", LoginRequest.Portal.STUDENT),
                "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        verify(loginAuditMapper).insert(any(com.educloud.user.entity.LoginAuditEntity.class));
    }

    @Test
    void wrongPasswordIncrementsFailureCountAndMayLock() {
        SysUserEntity user = activeUser();
        user.setFailedLoginCount(4);
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(
                new LoginRequest("student01", "wrong", LoginRequest.Portal.STUDENT),
                "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);

        // 第 5 次失败触发锁定状态更新（UpdateWrapper 参数化，断言 update 被调用；
        // 锁定生效后的拒绝行为由 lockedAccountIsRejected 用例覆盖）
        verify(sysUserMapper).update(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(
                        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class));
    }

    @Test
    void lockedAccountIsRejectedUntilLockExpires() {
        SysUserEntity user = activeUser();
        user.setStatus("LOCKED");
        user.setLockedUntil(NOW.plus(Duration.ofMinutes(10)));
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(user);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(), "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void disabledAccountIsRejected() {
        SysUserEntity user = activeUser();
        user.setStatus("DISABLED");
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(user);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(), "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void expiredLockIsClearedOnLogin() {
        SysUserEntity user = activeUser();
        user.setStatus("LOCKED");
        user.setLockedUntil(NOW.minusSeconds(1));
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(user);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(sysRoleMapper.selectCodesByUserId(1001L)).thenReturn(List.of());
        when(sysPermissionMapper.selectCodesByUserId(1001L)).thenReturn(List.of());
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(jwtEncoder.encode(any())).thenReturn("t");

        service.login(request(), "127.0.0.1", "ua", "req-1");

        verify(sysUserMapper).update(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        wrapper.getSqlSet().contains("locked_until")));
    }

    @Test
    void rejectsPortalMismatchBetweenAccountTypeAndLoginPortal() {
        // TEACHER 账号用 STUDENT portal 登录：应 403 ACCESS_DENIED，且不创建会话。
        SysUserEntity user = activeUser();
        user.setUserType("TEACHER");
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(user);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(), "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.educloud.common.error.CommonErrorCode.ACCESS_DENIED);
        org.mockito.Mockito.verify(refreshSessionMapper, org.mockito.Mockito.never())
                .insert(org.mockito.ArgumentMatchers.<com.educloud.user.entity.RefreshSessionEntity>any());
    }
}
