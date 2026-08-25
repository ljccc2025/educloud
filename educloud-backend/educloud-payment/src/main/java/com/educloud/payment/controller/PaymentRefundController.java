package com.educloud.payment.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.payment.dto.request.RefundApplyRequest;
import com.educloud.payment.dto.request.RefundAuditRequest;
import com.educloud.payment.dto.response.RefundDetailResponse;
import com.educloud.payment.security.JwtSecurityUtils;
import com.educloud.payment.service.RefundService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentRefundController {

    private final RefundService refundService;
    private final ApiResponseFactory responses;

    @PostMapping("/payments/refunds/apply")
    public ApiResponse<RefundDetailResponse> applyRefund(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RefundApplyRequest request) {
        Long userId = jwt != null ? JwtSecurityUtils.userId(jwt) : null;
        RefundDetailResponse response = refundService.applyRefund(userId, request);
        return responses.success(response);
    }

    // 安全修复（M08 审查）：退款审核/管理端点原先仅 anyRequest().authenticated()，
    // 任意学员可自申请自审批退款（垂直越权 + 资损）；现按 V008 权限码方法级鉴权。
    @PostMapping("/admin/payments/refunds/{id}/audit")
    @PreAuthorize("hasAuthority('refund:admin')")
    public ApiResponse<RefundDetailResponse> auditRefund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id,
            @Valid @RequestBody RefundAuditRequest request) {
        // @PreAuthorize 已保证 jwt 非空，不再默认 1L 兑底。
        Long adminUserId = JwtSecurityUtils.userId(jwt);
        RefundDetailResponse response = refundService.auditRefund(adminUserId, id, request);
        return responses.success(response);
    }

    @GetMapping("/admin/payments/refunds")
    @PreAuthorize("hasAuthority('refund:admin')")
    public ApiResponse<PageResponse<RefundDetailResponse>> listRefunds(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<RefundDetailResponse> response = refundService.listRefunds(status, page, size);
        return responses.success(response);
    }

    @GetMapping("/admin/payments/refunds/{id}")
    @PreAuthorize("hasAuthority('refund:admin')")
    public ApiResponse<RefundDetailResponse> getRefundDetail(@PathVariable("id") Long id) {
        RefundDetailResponse response = refundService.getRefundDetail(id);
        return responses.success(response);
    }
}
