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
public class ChannelBillItem {
    private String channelTradeNo;
    private Long paymentOrderId;
    private Long amountCents;
    private Long feeCents;
    private PaymentStatus status;
    private String tradeType;
    private LocalDateTime tradeTime;
}
