package com.educloud.payment.service;

import com.educloud.common.api.PageResponse;
import com.educloud.payment.dto.request.ReconcileDiffResolveRequest;
import com.educloud.payment.dto.response.ReconciliationBatchResponse;
import com.educloud.payment.dto.response.ReconciliationDiffResponse;
import com.educloud.payment.enums.PaymentChannel;

import java.time.LocalDate;

public interface ReconciliationService {

    ReconciliationBatchResponse runReconciliation(LocalDate date, PaymentChannel channel);

    ReconciliationDiffResponse resolveDiff(Long adminUserId, Long diffId, ReconcileDiffResolveRequest request);

    PageResponse<ReconciliationBatchResponse> listBatches(int page, int size);

    PageResponse<ReconciliationDiffResponse> listDiffs(Long batchId, int page, int size);
}
