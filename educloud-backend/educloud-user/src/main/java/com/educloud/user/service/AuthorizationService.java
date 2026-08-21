package com.educloud.user.service;

import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 授权摘要服务：汇总用户角色与权限码。
 * 依据：M03 设计规格第 6 节（JWT permissions 全量去重不得超过 64——Gateway 硬上限；
 * 超限 fail-fast，不签发截断载荷）。Redis 权限摘要缓存为后续优化项（设计规格第 15 节"建议缓存"）。
 */
@Service
public final class AuthorizationService {

    private static final int MAX_PERMISSIONS = 64;

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;

    public AuthorizationService(SysRoleMapper sysRoleMapper, SysPermissionMapper sysPermissionMapper) {
        this.sysRoleMapper = Objects.requireNonNull(sysRoleMapper, "sysRoleMapper");
        this.sysPermissionMapper = Objects.requireNonNull(sysPermissionMapper, "sysPermissionMapper");
    }

    public List<String> rolesFor(Long userId) {
        return List.copyOf(sysRoleMapper.selectCodesByUserId(userId));
    }

    public List<String> permissionsFor(Long userId) {
        Set<String> unique = new LinkedHashSet<>(sysPermissionMapper.selectCodesByUserId(userId));
        if (unique.size() > MAX_PERMISSIONS) {
            throw new IllegalStateException(
                    "User " + userId + " permission set exceeds the Gateway limit of " + MAX_PERMISSIONS);
        }
        return List.copyOf(unique);
    }
}
