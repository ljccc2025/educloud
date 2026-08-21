package com.educloud.user.service;

import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * RBAC 授权摘要单元测试。依据：M03 计划任务 11（去重、64 上限 fail-fast）。
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysPermissionMapper sysPermissionMapper;

    private AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(sysRoleMapper, sysPermissionMapper);
    }

    @Test
    void deduplicatesPermissions() {
        when(sysPermissionMapper.selectCodesByUserId(1001L))
                .thenReturn(List.of("user:read", "rbac:read", "user:read"));

        assertThat(service.permissionsFor(1001L))
                .containsExactly("user:read", "rbac:read");
    }

    @Test
    void acceptsUpToSixtyFourPermissions() {
        List<String> permissions = new ArrayList<>();
        IntStream.rangeClosed(1, 64).forEach(index -> permissions.add("perm:" + index));
        when(sysPermissionMapper.selectCodesByUserId(1001L)).thenReturn(permissions);

        assertThat(service.permissionsFor(1001L)).hasSize(64);
    }

    @Test
    void failsFastWhenPermissionSetExceedsGatewayLimit() {
        List<String> permissions = new ArrayList<>();
        IntStream.rangeClosed(1, 65).forEach(index -> permissions.add("perm:" + index));
        when(sysPermissionMapper.selectCodesByUserId(1001L)).thenReturn(permissions);

        assertThatThrownBy(() -> service.permissionsFor(1001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64");
    }

    @Test
    void rolesAreReturnedAsCodes() {
        when(sysRoleMapper.selectCodesByUserId(1001L)).thenReturn(List.of("STUDENT", "TEACHER"));

        assertThat(service.rolesFor(1001L)).containsExactly("STUDENT", "TEACHER");
    }
}
