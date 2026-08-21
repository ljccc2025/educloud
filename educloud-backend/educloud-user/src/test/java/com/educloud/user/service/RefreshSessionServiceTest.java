package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.config.JwtProperties;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.entity.RefreshSessionEntity;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.RefreshSessionMapper;
import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.security.ClaimsFactory;
import com.educloud.user.security.UserJwtEncoder;
import com.educloud.user.session.SessionFactory;
import com.educloud.user.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh 轮换单元测试。依据：M03 计划任务 8（原子轮换/并发宽限/窗口外重用撤销家族/
 * 指纹不一致撤销/禁用拒绝/Redis 版本校验；失败测试先行）。
 */
@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final String FAMILY = "family-1";
    private static final String FINGERPRINT = "fp-a";

    @Mock
    private RefreshSessionMapper refreshSessionMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysPermissionMapper sysPermissionMapper;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private SessionRevocationService revocationService;
    @Mock
    private UserJwtEncoder jwtEncoder;

    private RefreshSessionService service;

    @BeforeEach
    void setUp() {
        SessionProperties sessionProperties = new SessionProperties(
                "test", Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(5), false);
        JwtProperties jwtProperties = new JwtProperties(
                "/unused", "https://issuer.educloud.local", "educloud-api", Duration.ofMinutes(5));
        service = new RefreshSessionService(
                refreshSessionMapper,
                sysUserMapper,
                userProfileMapper,
                sysRoleMapper,
                sysPermissionMapper,
                new SessionFactory(),
                sessionStore,
                revocationService,
                new ClaimsFactory(jwtProperties),
                jwtEncoder,
                sessionProperties,
                org.mockito.Mockito.mock(com.educloud.user.observability.UserMetrics.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RefreshSessionEntity activeRow() {
        RefreshSessionEntity row = new RefreshSessionEntity();
        row.setId(10L);
        row.setFamilyId(FAMILY);
        row.setTokenId("parent-token");
        row.setUserId(1001L);
        row.setSessionTokenHash(SessionFactory.sha256Hex("raw-parent"));
        row.setStatus("ACTIVE");
        row.setClientType("STUDENT");
        row.setClientFingerprintHash(FINGERPRINT);
        row.setIssuedAt(NOW.minusSeconds(60));
        row.setExpiresAt(NOW.plus(Duration.ofDays(7)));
        return row;
    }

    private SysUserEntity activeUser() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setUsername("student01");
        user.setUserType("STUDENT");
        user.setStatus("ACTIVE");
        user.setTokenVersion(3L);
        return user;
    }

    @Test
    void rotatesAtomicallyAndKeepsFamilyId() {
        RefreshSessionEntity row = activeRow();
        when(refreshSessionMapper.selectByTokenHashForUpdate(any())).thenReturn(row);
        when(sysUserMapper.selectById(1001L)).thenReturn(activeUser());
        when(sessionStore.read(FAMILY)).thenReturn(Optional.of(
                new SessionStore.SessionSnapshot("ACTIVE", 3L)));
        when(refreshSessionMapper.markRotated(10L, NOW)).thenReturn(1);
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(jwtEncoder.encode(any())).thenReturn("new-access");

        RefreshSessionService.RefreshResult result =
                service.refresh("raw-parent", FINGERPRINT, "req-1");

        assertThat(result.response().accessToken()).isEqualTo("new-access");
        assertThat(result.refreshTokenRaw()).isNotEqualTo("raw-parent");
        var childCaptor = org.mockito.ArgumentCaptor.forClass(RefreshSessionEntity.class);
        verify(refreshSessionMapper).insert(childCaptor.capture());
        assertThat(childCaptor.getValue().getFamilyId()).isEqualTo(FAMILY);
        assertThat(childCaptor.getValue().getParentTokenId()).isEqualTo("parent-token");
        assertThat(childCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        verify(sessionStore).writeActive(
                org.mockito.ArgumentMatchers.eq(FAMILY),
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(15)));
    }

    @Test
    void rotatedWithinGraceWindowReturnsStableConflict() {
        RefreshSessionEntity row = activeRow();
        row.setStatus("ROTATED");
        row.setConsumedAt(NOW.minusSeconds(2));
        when(refreshSessionMapper.selectByTokenHashForUpdate(any())).thenReturn(row);

        assertThatThrownBy(() -> service.refresh("raw-parent", FINGERPRINT, "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.REFRESH_ALREADY_ROTATED);
        verify(revocationService, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void rotatedOutsideGraceWindowRevokesFamily() {
        RefreshSessionEntity row = activeRow();
        row.setStatus("ROTATED");
        row.setConsumedAt(NOW.minusSeconds(30));
        when(refreshSessionMapper.selectByTokenHashForUpdate(any())).thenReturn(row);

        assertThatThrownBy(() -> service.refresh("raw-parent", FINGERPRINT, "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.SESSION_REUSE_DETECTED);
        verify(revocationService).revokeFamily(FAMILY, "SESSION_REUSE_DETECTED", "req-1");
    }

    @Test
    void fingerprintMismatchRevokesFamily() {
        RefreshSessionEntity row = activeRow();
        when(refreshSessionMapper.selectByTokenHashForUpdate(any())).thenReturn(row);

        assertThatThrownBy(() -> service.refresh("raw-parent", "different-fp", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.SESSION_REUSE_DETECTED);
        verify(revocationService).revokeFamily(FAMILY, "CLIENT_FINGERPRINT_MISMATCH", "req-1");
    }

    @Test
    void disabledUserCannotRefresh() {
        RefreshSessionEntity row = activeRow();
        SysUserEntity user = activeUser();
        user.setStatus("DISABLED");
        when(refreshSessionMapper.selectByTokenHashForUpdate(any())).thenReturn(row);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);

        assertThatThrownBy(() -> service.refresh("raw-parent", FINGERPRINT, "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void tokenVersionMismatchWithReadModelRejectsRefresh() {
        RefreshSessionEntity row = activeRow();
        when(refreshSessionMapper.selectByTokenHashForUpdate(any())).thenReturn(row);
        when(sysUserMapper.selectById(1001L)).thenReturn(activeUser());
        when(sessionStore.read(FAMILY)).thenReturn(Optional.of(
                new SessionStore.SessionSnapshot("ACTIVE", 2L)));

        assertThatThrownBy(() -> service.refresh("raw-parent", FINGERPRINT, "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.SESSION_REVOKED);
    }
}
