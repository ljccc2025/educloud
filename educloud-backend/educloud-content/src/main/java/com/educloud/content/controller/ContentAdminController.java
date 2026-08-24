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

@RestController
@RequestMapping("/api/v1/admin/content-audits")
@RequiredArgsConstructor
public class ContentAdminController {

    private final ContentAuditService auditService;
    private final ApiResponseFactory responses;

    @GetMapping
    public ApiResponse<PageResponse<ContentAuditResponse>> listAudits(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<ContentAuditResponse> result = auditService.listAudits(status, page, size);
        return responses.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ContentAuditResponse> getAuditDetail(@PathVariable Long id) {
        ContentAuditResponse detail = auditService.getAuditDetail(id);
        return responses.success(detail);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approveAudit(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = (jwt != null) ? JwtSecurityUtils.userId(jwt) : 0L;
        auditService.approveAudit(id, adminId);
        return responses.success(null);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> rejectAudit(
            @PathVariable Long id,
            @Valid @RequestBody ContentAuditRejectRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = (jwt != null) ? JwtSecurityUtils.userId(jwt) : 0L;
        auditService.rejectAudit(id, request.getRejectReason(), adminId);
        return responses.success(null);
    }
}
