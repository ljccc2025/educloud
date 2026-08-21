package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.config.PasswordProperties;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.session.SessionStore;
import com.educloud.user.support.AuditWriter;
import com.educloud.user.support.PasswordPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 改密单元测试。依据：M03 计划任务 9（旧密码校验、token_version+1、撤销其他 family、
 * 当前 family Redis 以新版本重写 ACTIVE）。
 */
@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionRevocationService revocationService;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private OutboxWriter outboxWriter;
    @Mock
    private AuditWriter auditWriter;

    private PasswordChangeService service;

    @BeforeEach
    void setUp() {
        service = new PasswordChangeService(
                sysUserMapper,
                passwordEncoder,
                new PasswordPolicy(new PasswordProperties(8, 128, 10)),
                revocationService,
                sessionStore,
                new SessionProperties("test", Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(5), false),
                outboxWriter,
                auditWriter,
                new ObjectMapper());
    }

    private SysUserEntity user() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setPasswordHash("old-hash");
        user.setTokenVersion(3L);
        return user;
    }

    @Test
    void rejectsWrongOldPassword() {
        when(sysUserMapper.selectById(1001L)).thenReturn(user());
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                1001L, "wrong", "newpassword1", "family-1", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        verify(sysUserMapper, never()).update(isNull(), any());
    }

    @Test
    void rejectsWeakNewPassword() {
        when(sysUserMapper.selectById(1001L)).thenReturn(user());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                1001L, "oldpass", "short", "family-1", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.PASSWORD_WEAK);
    }

    @Test
    void changePasswordRevokesOthersAndRewritesCurrentFamilyReadModel() {
        when(sysUserMapper.selectById(1001L)).thenReturn(user());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");
        when(sysUserMapper.update(isNull(), any())).thenReturn(1);

        service.changePassword(1001L, "oldpass", "newpassword1", "family-1", "ip", "ua", "req-1");

        verify(revocationService).revokeAllForUserExcept(1001L, "family-1", "PASSWORD_CHANGED", "req-1");
        verify(sessionStore).writeActive(
                eq("family-1"), eq("1001"), eq(4L), eq(Duration.ofMinutes(15)));
        verify(auditWriter).write(any(AuditWriter.AuditEntry.class));
    }
}
