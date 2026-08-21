package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.user.dto.response.PermissionResponse;
import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.entity.SysPermissionEntity;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 权限目录接口（rbac:read；API 规范第 7 节）。 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final SysPermissionMapper permissionMapper;
    private final ApiResponseFactory responses;

    public PermissionController(SysPermissionMapper permissionMapper, ApiResponseFactory responses) {
        this.permissionMapper = permissionMapper;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rbac:read')")
    public ApiResponse<List<PermissionResponse>> list() {
        List<PermissionResponse> items = permissionMapper.selectList(
                        new QueryWrapper<SysPermissionEntity>().orderByAsc("id"))
                .stream()
                .map(permission -> new PermissionResponse(
                        String.valueOf(permission.getId()),
                        permission.getCode(),
                        permission.getName(),
                        permission.getResource(),
                        permission.getAction(),
                        permission.getDescription()))
                .toList();
        return responses.success(items);
    }
}
