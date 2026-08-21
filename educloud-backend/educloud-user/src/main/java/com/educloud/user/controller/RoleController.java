package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.user.dto.request.RoleCreateRequest;
import com.educloud.user.dto.request.RoleUpdateRequest;
import com.educloud.user.dto.response.RoleResponse;
import com.educloud.user.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色接口（rbac:read / rbac:manage；API 规范第 7 节）。 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;
    private final ApiResponseFactory responses;

    public RoleController(RoleService roleService, ApiResponseFactory responses) {
        this.roleService = roleService;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rbac:read')")
    public ApiResponse<List<RoleResponse>> list() {
        return responses.success(roleService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('rbac:manage')")
    public ApiResponse<RoleResponse> create(
            @Valid @RequestBody RoleCreateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return responses.success(roleService.create(
                request,
                jwt.getSubject(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                com.educloud.user.support.RequestIds.from(servletRequest)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rbac:manage')")
    public ApiResponse<RoleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return responses.success(roleService.update(
                id,
                request,
                jwt.getSubject(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                com.educloud.user.support.RequestIds.from(servletRequest)));
    }
}
