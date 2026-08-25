package com.educloud.payment.spi.model;

import com.educloud.payment.enums.PaymentChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundContext {
    private Long refundId;
    private Long paymentOrderId;
    private Long orderId;
    private String channelTradeNo;
    private Long totalAmountCents;
    private Long refundAmountCents;
    private String currency;
    private String reason;
    private PaymentChannel channel;
}
