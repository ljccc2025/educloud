package com.educloud.payment.spi.model;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.TradeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentContext {
    private Long paymentOrderId;
    private Long orderId;
    private Long userId;
    private Long amountCents;
    private String currency;
    private PaymentChannel channel;
    private TradeType tradeType;
    private String subject;
    private String description;
    private LocalDateTime expiresAt;
    private String clientIp;
}
