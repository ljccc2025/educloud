package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.user.dto.request.AssignRolesRequest;
import com.educloud.user.dto.request.UserStatusUpdateRequest;
import com.educloud.user.dto.response.UserAdminItem;
import com.educloud.user.service.UserAdminService;
import com.educloud.user.service.UserStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户接口。依据：API 规范第 7 节（user:read / user:status:update / rbac:assign；
 * 手机/邮箱脱敏）。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final UserStatusService userStatusService;
    private final ApiResponseFactory responses;

    public UserAdminController(
            UserAdminService userAdminService,
            UserStatusService userStatusService,
            ApiResponseFactory responses) {
        this.userAdminService = userAdminService;
        this.userStatusService = userStatusService;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<PageResponse<UserAdminItem>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(userAdminService.page(page, pageSize, Long.valueOf(jwt.getSubject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<UserAdminItem> detail(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return responses.success(userAdminService.detail(id, Long.valueOf(jwt.getSubject())));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:status:update')")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        userStatusService.updateStatus(
                id,
                request.status(),
                request.version(),
                request.reason(),
                jwt.getSubject(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                com.educloud.user.support.RequestIds.from(servletRequest));
        return responses.success(null);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('rbac:assign')")
    public ApiResponse<Void> assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody AssignRolesRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        userAdminService.assignRoles(
                id,
                request,
                jwt.getSubject(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                com.educloud.user.support.RequestIds.from(servletRequest));
        return responses.success(null);
    }
}
