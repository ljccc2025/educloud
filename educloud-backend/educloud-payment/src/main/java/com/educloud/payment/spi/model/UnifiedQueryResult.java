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
public class UnifiedQueryResult {
    private boolean success;
    private PaymentStatus status;
    private String channelTradeNo;
    private Long amountCents;
    private LocalDateTime paidAt;
    private String rawResponse;
    private String errorCode;
    private String errorMessage;
}
