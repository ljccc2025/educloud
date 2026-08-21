package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.RoleCreateRequest;
import com.educloud.user.dto.request.RoleUpdateRequest;
import com.educloud.user.dto.response.RoleResponse;
import com.educloud.user.entity.SysRoleEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.support.AuditWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 角色维护。依据：API 规范第 7 节（rbac:read / rbac:manage）；内置角色 code 不可修改。
 */
@Service
public class RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final AuditWriter auditWriter;

    public RoleService(SysRoleMapper sysRoleMapper, AuditWriter auditWriter) {
        this.sysRoleMapper = Objects.requireNonNull(sysRoleMapper, "sysRoleMapper");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
    }

    public List<RoleResponse> list() {
        return sysRoleMapper.selectList(
                        new QueryWrapper<SysRoleEntity>().orderByAsc("id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request, String actorId, String ip, String userAgent, String requestId) {
        SysRoleEntity role = new SysRoleEntity();
        role.setCode(request.code().trim());
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setStatus("ACTIVE");
        role.setBuiltIn(false);
        role.setCreatedAt(Instant.now());
        role.setUpdatedAt(Instant.now());
        role.setVersion(0);
        try {
            sysRoleMapper.insert(role);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(UserErrorCode.ROLE_NOT_FOUND, "Role code already exists: " + role.getCode());
        }
        auditWriter.write(new AuditWriter.AuditEntry(
                "USER", actorId == null ? "unknown" : actorId, null,
                "ROLE_CREATED", "role", String.valueOf(role.getId()),
                "SUCCESS", role.getCode(), null, null, ip, userAgent, requestId, null, "ADMIN"));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse update(Long roleId, RoleUpdateRequest request, String actorId, String ip, String userAgent, String requestId) {
        SysRoleEntity role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(UserErrorCode.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(UserErrorCode.ROLE_NOT_FOUND, "Built-in roles cannot be modified");
        }
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setUpdatedAt(Instant.now());
        sysRoleMapper.updateById(role);
        auditWriter.write(new AuditWriter.AuditEntry(
                "USER", actorId == null ? "unknown" : actorId, null,
                "ROLE_UPDATED", "role", String.valueOf(roleId),
                "SUCCESS", role.getCode(), null, null, ip, userAgent, requestId, null, "ADMIN"));
        return toResponse(role);
    }

    private RoleResponse toResponse(SysRoleEntity role) {
        return new RoleResponse(
                String.valueOf(role.getId()),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getStatus(),
                Boolean.TRUE.equals(role.getBuiltIn()));
    }
}
