package com.educloud.payment.dto.response;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.TradeType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentOrderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private Long amountCents;

    private String currency;

    private PaymentChannel channelCode;

    private TradeType tradeType;

    private PaymentStatus status;

    private String channelTradeNo;

    private String payUrl;

    private String qrCode;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;
}
