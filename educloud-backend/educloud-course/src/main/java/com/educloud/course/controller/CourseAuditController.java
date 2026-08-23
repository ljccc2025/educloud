package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.course.dto.request.AuditRejectRequest;
import com.educloud.course.dto.response.CourseAuditResponse;
import com.educloud.course.security.CoursePermissions;
import com.educloud.course.security.JwtSecurityUtils;
import com.educloud.course.service.CourseAuditService;
import jakarta.validation.Valid;
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
 * 课程审核控制器（M05 任务 9）：提交/审批/驳回/撤回 + 待审列表/详情 6 端点。
 *
 * <p>依据：规格 §6 —— submit-review 需 course:submit + 服务层归属校验；course-audits
 * 列表/详情/approve/reject 需 course:audit；withdraw 无独立权限码（仅提交教师本人，
 * 身份校验在服务层，默认 Security 链已要求 authenticated）。当前用户由 JWT subject
 * （userId）解析（JwtSecurityUtils）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CourseAuditController {

    private final CourseAuditService auditService;
    private final ApiResponseFactory responses;

    public CourseAuditController(CourseAuditService auditService, ApiResponseFactory responses) {
        this.auditService = auditService;
        this.responses = responses;
    }

    @PostMapping("/course-drafts/{versionId}/submit-review")
    @PreAuthorize("hasAuthority('" + CoursePermissions.SUBMIT + "')")
    public ApiResponse<CourseAuditResponse> submitReview(
            @PathVariable Long versionId,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(auditService.submitForReview(versionId, JwtSecurityUtils.userId(jwt)));
    }

    @GetMapping("/course-audits")
    @PreAuthorize("hasAuthority('" + CoursePermissions.AUDIT + "')")
    public ApiResponse<PageResponse<CourseAuditResponse>> listPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return responses.success(auditService.listPending(page, pageSize));
    }

    @GetMapping("/course-audits/{auditId}")
    @PreAuthorize("hasAuthority('" + CoursePermissions.AUDIT + "')")
    public ApiResponse<CourseAuditResponse> detail(@PathVariable Long auditId) {
        return responses.success(auditService.getDetail(auditId));
    }

    @PostMapping("/course-audits/{auditId}/approve")
    @PreAuthorize("hasAuthority('" + CoursePermissions.AUDIT + "')")
    public ApiResponse<CourseAuditResponse> approve(
            @PathVariable Long auditId,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(auditService.approve(auditId, JwtSecurityUtils.userId(jwt)));
    }

    @PostMapping("/course-audits/{auditId}/reject")
    @PreAuthorize("hasAuthority('" + CoursePermissions.AUDIT + "')")
    public ApiResponse<CourseAuditResponse> reject(
            @PathVariable Long auditId,
            @Valid @RequestBody AuditRejectRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(
                auditService.reject(auditId, JwtSecurityUtils.userId(jwt), request.reason()));
    }

    @PostMapping("/course-audits/{auditId}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CourseAuditResponse> withdraw(
            @PathVariable Long auditId,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(auditService.withdraw(auditId, JwtSecurityUtils.userId(jwt)));
    }
}
