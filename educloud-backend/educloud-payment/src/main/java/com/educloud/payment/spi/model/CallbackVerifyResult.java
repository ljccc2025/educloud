package com.educloud.payment.spi.model;

import com.educloud.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackVerifyResult {
    private boolean valid;
    private Long paymentOrderId;
    private String notifyId;
    private String channelTradeNo;
    private Long amountCents;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private String rawPayload;
    private String responseMessage;
    private String errorMessage;
}
