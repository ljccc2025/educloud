package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.AssignRolesRequest;
import com.educloud.user.dto.response.UserAdminItem;
import com.educloud.user.entity.SysRoleEntity;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.SysUserRoleMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.support.AuditWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 管理端用户服务单元测试（脱敏、角色分配、审计与事件）。 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

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

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(
                sysUserMapper, userProfileMapper, sysRoleMapper, sysUserRoleMapper,
                outboxWriter, auditWriter, new ObjectMapper());
    }

    @Test
    void detailMasksContactFields() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setUsername("student01");
        user.setEmail("student01@example.com");
        user.setPhone("13800000001");
        user.setUserType("STUDENT");
        user.setStatus("ACTIVE");
        user.setVersion(3);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(null);

        UserAdminItem item = service.detail(1001L);

        assertThat(item.emailMasked()).doesNotContain("student01@");
        assertThat(item.phoneMasked()).isNotEqualTo("13800000001");
    }

    @Test
    void assignRolesRequiresExistingRoles() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setVersion(1);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(sysRoleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.assignRoles(
                1001L, new AssignRolesRequest(List.of("NOPE")), "1", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    void assignRolesPublishesEventAndAudit() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setVersion(2);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        SysRoleEntity role = new SysRoleEntity();
        role.setId(2L);
        role.setCode("TEACHER");
        role.setStatus("ACTIVE");
        when(sysRoleMapper.selectOne(any())).thenReturn(role);
        when(sysUserRoleMapper.delete(any())).thenReturn(1);
        when(sysUserRoleMapper.insert(any(com.educloud.user.entity.SysUserRoleEntity.class))).thenReturn(1);

        service.assignRoles(
                1001L, new AssignRolesRequest(List.of("TEACHER")), "1", "ip", "ua", "req-1");

        verify(outboxWriter).write(
                org.mockito.ArgumentMatchers.eq("User"),
                org.mockito.ArgumentMatchers.eq("1001"),
                org.mockito.ArgumentMatchers.eq("RoleAssignmentChanged"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("req-1"),
                org.mockito.ArgumentMatchers.isNull());
        verify(auditWriter).write(any(AuditWriter.AuditEntry.class));
    }
}
