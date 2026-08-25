package com.educloud.payment.dto.response;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.RefundStatus;
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
public class RefundDetailResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentOrderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundRequestId;

    private Long refundAmountCents;

    private String currency;

    private String reason;

    private PaymentChannel channelCode;

    private String channelRefundNo;

    private RefundStatus status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long auditedBy;

    private LocalDateTime auditedAt;

    private String auditRemark;

    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;
}
