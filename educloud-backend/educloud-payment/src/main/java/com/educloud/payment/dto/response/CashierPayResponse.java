package com.educloud.payment.dto.response;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
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
public class CashierPayResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentOrderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    private PaymentChannel channelCode;

    private Long amountCents;

    private String currency;

    private String payUrl;

    private String qrCode;

    private PaymentStatus status;

    private LocalDateTime expiresAt;
}
