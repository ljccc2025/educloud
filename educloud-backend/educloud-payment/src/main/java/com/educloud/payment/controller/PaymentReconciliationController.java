package com.educloud.payment.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.payment.dto.request.ReconcileDiffResolveRequest;
import com.educloud.payment.dto.request.ReconcileTriggerRequest;
import com.educloud.payment.dto.response.ReconciliationBatchResponse;
import com.educloud.payment.dto.response.ReconciliationDiffResponse;
import com.educloud.payment.security.JwtSecurityUtils;
import com.educloud.payment.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/admin/payments/reconciliation")
@RequiredArgsConstructor
public class PaymentReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ApiResponseFactory responses;

    @PostMapping("/trigger")
    public ApiResponse<ReconciliationBatchResponse> triggerReconciliation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReconcileTriggerRequest request) {
        ReconciliationBatchResponse response = reconciliationService.runReconciliation(
                request.getReconcileDate(), request.getChannelCode());
        return responses.success(response);
    }

    @GetMapping("/batches")
    public ApiResponse<PageResponse<ReconciliationBatchResponse>> listBatches(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<ReconciliationBatchResponse> response = reconciliationService.listBatches(page, size);
        return responses.success(response);
    }

    @GetMapping("/batches/{id}/diffs")
    public ApiResponse<PageResponse<ReconciliationDiffResponse>> listDiffs(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<ReconciliationDiffResponse> response = reconciliationService.listDiffs(id, page, size);
        return responses.success(response);
    }

    @PostMapping("/diffs/{id}/resolve")
    public ApiResponse<ReconciliationDiffResponse> resolveDiff(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id,
            @Valid @RequestBody ReconcileDiffResolveRequest request) {
        Long adminUserId = jwt != null ? JwtSecurityUtils.userId(jwt) : 1L;
        ReconciliationDiffResponse response = reconciliationService.resolveDiff(adminUserId, id, request);
        return responses.success(response);
    }
}
