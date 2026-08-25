package com.educloud.payment.service;

import com.educloud.payment.dto.request.CashierPayRequest;
import com.educloud.payment.dto.response.CashierPayResponse;
import com.educloud.payment.dto.response.PaymentDetailResponse;
import com.educloud.payment.entity.PaymentOrderEntity;

public interface PaymentService {

    CashierPayResponse createCashierPayment(Long userId, CashierPayRequest request);

    PaymentDetailResponse getPaymentDetail(Long userId, Long paymentOrderId);

    PaymentDetailResponse mockConfirmPayment(Long userId, Long paymentOrderId);

    PaymentOrderEntity getById(Long paymentOrderId);
}
