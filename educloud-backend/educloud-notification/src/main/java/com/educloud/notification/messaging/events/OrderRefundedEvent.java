package com.educloud.notification.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundedEvent {
    private String eventId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String refundReason;
}
