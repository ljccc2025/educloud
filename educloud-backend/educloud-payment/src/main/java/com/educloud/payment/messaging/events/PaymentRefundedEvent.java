package com.educloud.payment.messaging.events;

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
public class PaymentRefundedEvent {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentOrderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private Long refundAmountCents;

    private LocalDateTime refundedAt;
}
