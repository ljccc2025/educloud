package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
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

/**
 * 档案服务单元测试。依据：M03 计划任务 10（本人读写、头像 fileId 只存不调 File）。
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

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(
                sysUserMapper, userProfileMapper, sysRoleMapper, sysPermissionMapper, auditWriter);
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
    }

    @Test
    void updateProfileStoresAvatarFileIdWithoutCallingFileService() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        when(sysUserMapper.selectById(1001L)).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfileEntity.class))).thenReturn(1);

        ProfileResponse response = service.updateProfile(
                1001L,
                new ProfileUpdateRequest("新名字", "简介", "zh-CN", 9001L),
                "ip", "ua", "req-1");

        assertThat(response.displayName()).isEqualTo("新名字");
        assertThat(response.avatarFileId()).isEqualTo("9001");
        verify(userProfileMapper).insert(any(UserProfileEntity.class));
        verify(auditWriter).write(any(AuditWriter.AuditEntry.class));
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
