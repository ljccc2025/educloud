package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.dto.request.ProfileUpdateRequest;
import com.educloud.user.dto.response.ProfileResponse;
import com.educloud.user.dto.response.UserSummary;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.support.AuditWriter;
import com.educloud.user.support.FileClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 档案服务单元测试。依据：M03 计划任务 10 与 M04 计划任务 14（avatarFileId 变更先
 * File bind/unbind 后落库；bind 失败抛 DEPENDENCY_UNAVAILABLE 且不落库；me 返回 avatarUrl）。
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysPermissionMapper sysPermissionMapper;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private FileClient fileClient;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(
                sysUserMapper, userProfileMapper, sysRoleMapper, sysPermissionMapper,
                auditWriter, fileClient);
    }

    @Test
    void meBuildsSummaryFromUserAndRbac() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setUsername("student01");
        user.setUserType("STUDENT");
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(sysRoleMapper.selectCodesByUserId(1001L)).thenReturn(List.of("STUDENT"));
        when(sysPermissionMapper.selectCodesByUserId(1001L)).thenReturn(List.of("course:read"));
        when(userProfileMapper.selectOne(any())).thenReturn(null);

        UserSummary summary = service.me(1001L);

        assertThat(summary.username()).isEqualTo("student01");
        assertThat(summary.roles()).containsExactly("STUDENT");
        assertThat(summary.displayName()).isEqualTo("student01");
        assertThat(summary.avatarUrl()).isNull();
        verifyNoInteractions(fileClient);
    }

    @Test
    void meReturnsGrantedAvatarUrl() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setUsername("student01");
        user.setUserType("STUDENT");
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(1001L);
        profile.setAvatarFileId(9001L);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(sysRoleMapper.selectCodesByUserId(1001L)).thenReturn(List.of());
        when(sysPermissionMapper.selectCodesByUserId(1001L)).thenReturn(List.of());
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(fileClient.grantAvatarUrls(List.of(9001L), 1001L))
                .thenReturn(Map.of(9001L, "http://bucket/avatar-9001"));

        UserSummary summary = service.me(1001L);

        assertThat(summary.avatarUrl()).isEqualTo("http://bucket/avatar-9001");
    }

    @Test
    void updateProfileBindsNewAvatarBeforePersisting() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfileEntity.class))).thenReturn(1);

        ProfileResponse response = service.updateProfile(
                1001L,
                new ProfileUpdateRequest("新名字", "简介", "zh-CN", 9001L),
                "ip", "ua", "req-1");

        InOrder inOrder = inOrder(fileClient, userProfileMapper);
        inOrder.verify(fileClient).bindAvatar(1001L, 9001L);
        inOrder.verify(userProfileMapper).insert(any(UserProfileEntity.class));
        assertThat(response.avatarFileId()).isEqualTo("9001");
        verify(auditWriter).write(any(AuditWriter.AuditEntry.class));
    }

    @Test
    void updateProfileSkipsBindWhenAvatarUnchanged() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setId(1L);
        profile.setUserId(1001L);
        profile.setAvatarFileId(9001L);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProfileMapper.updateById(any(UserProfileEntity.class))).thenReturn(1);

        service.updateProfile(
                1001L,
                new ProfileUpdateRequest("新名字", "简介", "zh-CN", 9001L),
                "ip", "ua", "req-1");

        verifyNoInteractions(fileClient);
        verify(userProfileMapper).updateById(any(UserProfileEntity.class));
    }

    @Test
    void updateProfileUnbindsOldAvatarWhenCleared() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setId(1L);
        profile.setUserId(1001L);
        profile.setAvatarFileId(9001L);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProfileMapper.updateById(any(UserProfileEntity.class))).thenReturn(1);

        ProfileResponse response = service.updateProfile(
                1001L,
                new ProfileUpdateRequest("新名字", "简介", "zh-CN", null),
                "ip", "ua", "req-1");

        verify(fileClient).unbindAvatar(1001L, 9001L);
        assertThat(response.avatarFileId()).isNull();
        verify(userProfileMapper).updateById(any(UserProfileEntity.class));
    }

    @Test
    void updateProfileBindFailurePropagatesAndDoesNotPersist() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        doThrow(new BusinessException(
                CommonErrorCode.DEPENDENCY_UNAVAILABLE, "File service unavailable"))
                .when(fileClient).bindAvatar(1001L, 9001L);

        assertThatThrownBy(() -> service.updateProfile(
                1001L,
                new ProfileUpdateRequest("新名字", "简介", "zh-CN", 9001L),
                "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);

        verify(userProfileMapper, never()).insert(any(UserProfileEntity.class));
        verify(userProfileMapper, never()).updateById(any(UserProfileEntity.class));
        verify(auditWriter, never()).write(any());
    }

    @Test
    void rejectsUnknownUser() {
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.me(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }
}
