package com.educloud.payment.messaging.events;

import com.educloud.payment.enums.PaymentChannel;
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
public class PaymentSucceededEvent {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentOrderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private Long amountCents;

    private PaymentChannel channelCode;

    private String channelTradeNo;

    private LocalDateTime paidAt;
}
