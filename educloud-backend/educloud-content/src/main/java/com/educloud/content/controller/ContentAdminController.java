package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.content.dto.request.ContentAuditRejectRequest;
import com.educloud.content.dto.response.ContentAuditResponse;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.ContentAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容审核管理接口（/api/v1/admin/content-audits）。
 *
 * <p>审查修复 BUG-001（P0）：原实现仅 anyRequest().authenticated()，任何持有效 JWT
 * 的用户（含学生）都可查看含完整内容快照的审核单、教师可自审自发。现全部端点补
 * @PreAuthorize("hasAuthority('content:audit')")（permissions claim → authorities，
 * SecurityConfig#jwtAuthenticationConverter），并删除 adminId 的 0L 兜底——未认证
 * 请求进不到方法（anyRequest().authenticated()），jwt 为 null 直接失败。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/content-audits")
@RequiredArgsConstructor
public class ContentAdminController {

    private final ContentAuditService auditService;
    private final ApiResponseFactory responses;

    @GetMapping
    @PreAuthorize("hasAuthority('content:audit')")
    public ApiResponse<PageResponse<ContentAuditResponse>> listAudits(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<ContentAuditResponse> result = auditService.listAudits(status, page, size);
        return responses.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('content:audit')")
    public ApiResponse<ContentAuditResponse> getAuditDetail(@PathVariable Long id) {
        ContentAuditResponse detail = auditService.getAuditDetail(id);
        return responses.success(detail);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('content:audit')")
    public ApiResponse<Void> approveAudit(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = JwtSecurityUtils.userId(jwt);
        auditService.approveAudit(id, adminId);
        return responses.success(null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('content:audit')")
    public ApiResponse<Void> rejectAudit(
            @PathVariable Long id,
            @Valid @RequestBody ContentAuditRejectRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = JwtSecurityUtils.userId(jwt);
        auditService.rejectAudit(id, request.getRejectReason(), adminId);
        return responses.success(null);
    }
}
