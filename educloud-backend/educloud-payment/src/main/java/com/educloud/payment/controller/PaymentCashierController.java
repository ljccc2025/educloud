package com.educloud.payment.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.payment.dto.request.CashierPayRequest;
import com.educloud.payment.dto.response.CashierPayResponse;
import com.educloud.payment.dto.response.PaymentDetailResponse;
import com.educloud.payment.security.JwtSecurityUtils;
import com.educloud.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentCashierController {

    private final PaymentService paymentService;
    private final ApiResponseFactory responses;

    @PostMapping("/cashier")
    public ApiResponse<CashierPayResponse> createCashierPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CashierPayRequest request) {
        Long userId = jwt != null ? JwtSecurityUtils.userId(jwt) : null;
        CashierPayResponse response = paymentService.createCashierPayment(userId, request);
        return responses.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentDetailResponse> getPaymentDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = jwt != null ? JwtSecurityUtils.userId(jwt) : null;
        PaymentDetailResponse response = paymentService.getPaymentDetail(userId, id);
        return responses.success(response);
    }

    @PostMapping("/{id}/mock-confirm")
    public ApiResponse<PaymentDetailResponse> mockConfirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = jwt != null ? JwtSecurityUtils.userId(jwt) : null;
        PaymentDetailResponse response = paymentService.mockConfirmPayment(userId, id);
        return responses.success(response);
    }
}
