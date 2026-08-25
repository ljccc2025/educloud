package com.educloud.payment.service;

import com.educloud.common.api.PageResponse;
import com.educloud.payment.dto.request.RefundApplyRequest;
import com.educloud.payment.dto.request.RefundAuditRequest;
import com.educloud.payment.dto.response.RefundDetailResponse;

public interface RefundService {

    RefundDetailResponse applyRefund(Long userId, RefundApplyRequest request);

    RefundDetailResponse auditRefund(Long adminUserId, Long refundId, RefundAuditRequest request);

    RefundDetailResponse getRefundDetail(Long refundId);

    PageResponse<RefundDetailResponse> listRefunds(String status, int page, int size);
}
