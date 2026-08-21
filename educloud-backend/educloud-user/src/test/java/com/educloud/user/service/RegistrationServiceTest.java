package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.config.PasswordProperties;
import com.educloud.user.config.RegistrationProperties;
import com.educloud.user.dto.request.RegisterStudentRequest;
import com.educloud.user.entity.SysRoleEntity;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.entity.SysUserRoleEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.SysUserRoleMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.support.AuditWriter;
import com.educloud.user.support.PasswordPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生注册单元测试。依据：M03 计划任务 6（开关/密码策略/唯一冲突/默认 STUDENT 角色/
 * UserRegistered Outbox 行/审计；失败测试先行）。
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private OutboxWriter outboxWriter;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private PasswordEncoder passwordEncoder;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        PasswordPolicy policy = new PasswordPolicy(
                new PasswordProperties(8, 128, 10));
        service = new RegistrationService(
                new RegistrationProperties(true),
                policy,
                passwordEncoder,
                sysUserMapper,
                userProfileMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                outboxWriter,
                auditWriter,
                new ObjectMapper(),
                org.mockito.Mockito.mock(com.educloud.user.observability.UserMetrics.class));
    }

    private RegisterStudentRequest request() {
        return new RegisterStudentRequest("student01", "password123", "s@example.com", null, "学生01");
    }

    @Test
    void rejectsRegistrationWhenDisabled() {
        service = new RegistrationService(
                new RegistrationProperties(false),
                new PasswordPolicy(new PasswordProperties(8, 128, 10)),
                passwordEncoder,
                sysUserMapper,
                userProfileMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                outboxWriter,
                auditWriter,
                new ObjectMapper(),
                org.mockito.Mockito.mock(com.educloud.user.observability.UserMetrics.class));

        assertThatThrownBy(() -> service.register(request(), "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.REGISTRATION_DISABLED);
    }

    @Test
    void rejectsWeakPassword() {
        RegisterStudentRequest weak = new RegisterStudentRequest(
                "student01", "short", "s@example.com", null, null);

        assertThatThrownBy(() -> service.register(weak, "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.PASSWORD_WEAK);
        verify(sysUserMapper, never()).insert(any(SysUserEntity.class));
    }

    @Test
    void rejectsDuplicateUsername() {
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(new SysUserEntity());

        assertThatThrownBy(() -> service.register(request(), "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.USERNAME_TAKEN);
    }

    @Test
    void rejectsDuplicateEmailOnInsertRace() {
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(null);
        lenient().when(sysUserMapper.selectByEmail("s@example.com"))
                .thenReturn(null, new SysUserEntity());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        doAnswer(invocation -> {
            throw new DuplicateKeyException("uk_user_email");
        }).when(sysUserMapper).insert(any(SysUserEntity.class));

        assertThatThrownBy(() -> service.register(request(), "127.0.0.1", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.EMAIL_TAKEN);
    }

    @Test
    void registersStudentWithDefaultRoleOutboxAndAudit() {
        when(sysUserMapper.selectByLoginName("student01")).thenReturn(null);
        when(sysUserMapper.selectByEmail("s@example.com")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        doAnswer(invocation -> {
            SysUserEntity user = invocation.getArgument(0);
            user.setId(1960000000000000001L);
            return 1;
        }).when(sysUserMapper).insert(any(SysUserEntity.class));

        SysRoleEntity studentRole = new SysRoleEntity();
        studentRole.setId(1L);
        studentRole.setCode("STUDENT");
        studentRole.setStatus("ACTIVE");
        when(sysRoleMapper.selectOne(any())).thenReturn(studentRole);

        Long userId = service.register(request(), "127.0.0.1", "ua", "req-1");

        assertThat(userId).isEqualTo(1960000000000000001L);
        var userCaptor = org.mockito.ArgumentCaptor.forClass(SysUserEntity.class);
        verify(sysUserMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserType()).isEqualTo("STUDENT");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");

        verify(userProfileMapper).insert(any(UserProfileEntity.class));
        verify(sysUserRoleMapper).insert(any(SysUserRoleEntity.class));
        verify(outboxWriter).write(
                org.mockito.ArgumentMatchers.eq("User"),
                org.mockito.ArgumentMatchers.eq("1960000000000000001"),
                org.mockito.ArgumentMatchers.eq("UserRegistered"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("req-1"),
                org.mockito.ArgumentMatchers.isNull());
        verify(auditWriter).write(any(AuditWriter.AuditEntry.class));
    }
}
