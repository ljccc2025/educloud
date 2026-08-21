package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.support.AuditWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户状态管理单元测试。依据：M03 计划任务 9（乐观锁、禁用撤销+tokenVersion+1+事件、恢复）。
 */
@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SessionRevocationService revocationService;
    @Mock
    private OutboxWriter outboxWriter;
    @Mock
    private AuditWriter auditWriter;

    private UserStatusService service;

    @BeforeEach
    void setUp() {
        service = new UserStatusService(
                sysUserMapper, revocationService, outboxWriter, auditWriter, new ObjectMapper());
    }

    private SysUserEntity user() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setStatus("ACTIVE");
        user.setTokenVersion(2L);
        user.setVersion(5);
        return user;
    }

    @Test
    void rejectsMissingUserAndInvalidStatus() {
        when(sysUserMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateStatus(
                999L, "DISABLED", 1, "reason", "admin", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void rejectsStaleVersionWithConflict() {
        when(sysUserMapper.selectById(1001L)).thenReturn(user());

        assertThatThrownBy(() -> service.updateStatus(
                1001L, "DISABLED", 4, "reason", "admin", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CommonErrorCode.VERSION_CONFLICT);
    }

    @Test
    void disableRevokesSessionsIncrementsVersionAndPublishesEvent() {
        when(sysUserMapper.selectById(1001L)).thenReturn(user());
        when(sysUserMapper.update(isNull(), any())).thenReturn(1);

        service.updateStatus(1001L, "DISABLED", 5, "abuse", "admin", "ip", "ua", "req-1");

        verify(revocationService).revokeAllForUser(1001L, "ACCOUNT_DISABLED", "req-1");
        verify(outboxWriter).write(
                org.mockito.ArgumentMatchers.eq("User"),
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq("UserStatusChanged"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(6L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("req-1"),
                org.mockito.ArgumentMatchers.isNull());
        verify(auditWriter).write(any(AuditWriter.AuditEntry.class));
    }

    @Test
    void restoreDoesNotRevokeSessions() {
        when(sysUserMapper.selectById(1001L)).thenReturn(user());
        when(sysUserMapper.update(isNull(), any())).thenReturn(1);

        service.updateStatus(1001L, "ACTIVE", 5, "reviewed", "admin", "ip", "ua", "req-1");

        verify(revocationService, never()).revokeAllForUser(any(), any(), any());
    }
}
