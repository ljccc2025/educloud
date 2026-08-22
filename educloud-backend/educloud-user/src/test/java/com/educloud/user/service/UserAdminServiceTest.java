package com.educloud.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.AssignRolesRequest;
import com.educloud.user.dto.response.UserAdminItem;
import com.educloud.user.entity.SysRoleEntity;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.SysUserRoleMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.support.AuditWriter;
import com.educloud.user.support.FileClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 管理端用户服务单元测试（脱敏、角色分配、审计与事件、avatarUrl 组装）。 */
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
    @Mock
    private FileClient fileClient;

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(
                sysUserMapper, userProfileMapper, sysRoleMapper, sysUserRoleMapper,
                outboxWriter, auditWriter, new ObjectMapper(), fileClient);
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
        assertThat(item.avatarUrl()).isNull();
        verifyNoInteractions(fileClient);
    }

    @Test
    void detailAssemblesAvatarUrl() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setUsername("student01");
        user.setUserType("STUDENT");
        user.setStatus("ACTIVE");
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(1001L);
        profile.setAvatarFileId(9001L);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(fileClient.grantAvatarUrls(
                eq(List.of(9001L)), eq(999L), eq(Map.of(9001L, 1001L))))
                .thenReturn(Map.of(9001L, "http://bucket/avatar-9001"));

        UserAdminItem item = service.detail(1001L, 999L);

        assertThat(item.avatarUrl()).isEqualTo("http://bucket/avatar-9001");
    }

    @Test
    void pageAssemblesAvatarUrlsWithSingleBatchGrant() {
        SysUserEntity u1 = user(1001L);
        SysUserEntity u2 = user(1002L);
        UserProfileEntity p1 = profile(1001L, 9001L);
        UserProfileEntity p2 = profile(1002L, 9002L);
        Page<SysUserEntity> result = new Page<>(1, 10);
        result.setRecords(List.of(u1, u2));
        result.setTotal(2);
        when(sysUserMapper.selectPage(any(), any())).thenReturn(result);
        when(userProfileMapper.selectList(any())).thenReturn(List.of(p1, p2));
        when(fileClient.grantAvatarUrls(
                eq(List.of(9001L, 9002L)), eq(999L),
                eq(Map.of(9001L, 1001L, 9002L, 1002L))))
                .thenReturn(Map.of(9001L, "http://bucket/avatar-9001", 9002L, "http://bucket/avatar-9002"));

        var page = service.page(1, 10, 999L);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).avatarUrl()).isEqualTo("http://bucket/avatar-9001");
        assertThat(page.items().get(1).avatarUrl()).isEqualTo("http://bucket/avatar-9002");
        verify(fileClient, times(1)).grantAvatarUrls(
                eq(List.of(9001L, 9002L)), eq(999L), eq(Map.of(9001L, 1001L, 9002L, 1002L)));
    }

    @Test
    void pageSkipsGrantWhenNoAvatars() {
        Page<SysUserEntity> result = new Page<>(1, 10);
        result.setRecords(List.of(user(1001L)));
        result.setTotal(1);
        when(sysUserMapper.selectPage(any(), any())).thenReturn(result);
        when(userProfileMapper.selectList(any())).thenReturn(List.of());

        var page = service.page(1, 10, 999L);

        assertThat(page.items().get(0).avatarUrl()).isNull();
        verifyNoInteractions(fileClient);
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

    private static SysUserEntity user(Long id) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setUserType("STUDENT");
        user.setStatus("ACTIVE");
        return user;
    }

    private static UserProfileEntity profile(Long userId, Long avatarFileId) {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setAvatarFileId(avatarFileId);
        return profile;
    }
}