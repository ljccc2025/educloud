package com.educloud.payment.spi.model;

import com.educloud.payment.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedRefundResult {
    private boolean success;
    private RefundStatus status;
    private String channelRefundNo;
    private Long refundAmountCents;
    private LocalDateTime refundedAt;
    private String rawResponse;
    private String errorCode;
    private String errorMessage;
}
