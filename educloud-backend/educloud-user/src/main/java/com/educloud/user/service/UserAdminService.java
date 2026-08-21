package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.AssignRolesRequest;
import com.educloud.user.dto.response.UserAdminItem;
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
import com.educloud.user.support.Masking;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理端用户查询与角色分配。依据：API 规范第 7 节（分页脱敏、user:read / rbac:assign；
 * 无权感知返回 404 由控制器权限决定）与 M03 设计规格第 10 节。
 */
@Service
public final class UserAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public UserAdminService(
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            OutboxWriter outboxWriter,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.sysUserMapper = Objects.requireNonNull(sysUserMapper, "sysUserMapper");
        this.userProfileMapper = Objects.requireNonNull(userProfileMapper, "userProfileMapper");
        this.sysRoleMapper = Objects.requireNonNull(sysRoleMapper, "sysRoleMapper");
        this.sysUserRoleMapper = Objects.requireNonNull(sysUserRoleMapper, "sysUserRoleMapper");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public PageResponse<UserAdminItem> page(int page, int pageSize) {
        int boundedSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<SysUserEntity> result = sysUserMapper.selectPage(
                new Page<>(Math.max(page, 1), boundedSize),
                new QueryWrapper<SysUserEntity>().orderByDesc("created_at"));
        List<UserAdminItem> items = result.getRecords().stream()
                .map(this::toItem)
                .toList();
        return PageResponse.of(
                items,
                Math.max(page, 1),
                boundedSize,
                result.getTotal());
    }

    public UserAdminItem detail(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return toItem(user);
    }

    @Transactional
    public void assignRoles(Long userId, AssignRolesRequest request, String actorId, String ip, String userAgent, String requestId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        List<String> codes = request.roleCodes().stream().map(String::trim).distinct().toList();
        List<SysRoleEntity> roles = codes.stream()
                .map(code -> sysRoleMapper.selectOne(
                        new QueryWrapper<SysRoleEntity>()
                                .eq("code", code)
                                .eq("status", "ACTIVE")))
                .toList();
        for (int index = 0; index < codes.size(); index++) {
            if (roles.get(index) == null) {
                throw new BusinessException(UserErrorCode.ROLE_NOT_FOUND, "Role not found: " + codes.get(index));
            }
        }
        sysUserRoleMapper.delete(new QueryWrapper<SysUserRoleEntity>()
                .eq("user_id", userId));
        Instant now = Instant.now();
        for (SysRoleEntity role : roles) {
            SysUserRoleEntity binding = new SysUserRoleEntity();
            binding.setUserId(userId);
            binding.setRoleId(role.getId());
            binding.setAssignedBy(actorId == null ? null : Long.valueOf(actorId));
            binding.setAssignedAt(now);
            sysUserRoleMapper.insert(binding);
        }
        outboxWriter.write(
                "User",
                String.valueOf(userId),
                "RoleAssignmentChanged",
                1,
                user.getVersion() + 1L,
                payload(userId, codes),
                requestId,
                null);
        auditWriter.write(new AuditWriter.AuditEntry(
                "USER",
                actorId == null ? "unknown" : actorId,
                null,
                "ROLE_ASSIGNED",
                "user",
                String.valueOf(userId),
                "SUCCESS",
                String.join(",", codes),
                null,
                null,
                ip,
                userAgent,
                requestId,
                null,
                "ADMIN"));
    }

    private UserAdminItem toItem(SysUserEntity user) {
        UserProfileEntity profile = userProfileMapper.selectOne(
                new QueryWrapper<UserProfileEntity>().eq("user_id", user.getId()));
        return new UserAdminItem(
                String.valueOf(user.getId()),
                user.getUsername(),
                Masking.userIdentifier(user.getEmail()),
                Masking.userIdentifier(user.getPhone()),
                user.getUserType(),
                user.getStatus(),
                profile == null ? user.getUsername() : profile.getDisplayName(),
                user.getCreatedAt(),
                user.getVersion());
    }

    private String payload(Long userId, List<String> roleCodes) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "userId", String.valueOf(userId),
                    "roleCodes", roleCodes));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize RoleAssignmentChanged payload", exception);
        }
    }
}
