package com.educloud.payment.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundApplyRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentOrderId;

    @NotNull(message = "订单ID不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundRequestId;

    @NotNull(message = "退款金额不能为空")
    @Min(value = 1, message = "退款金额必须大于 0 分")
    private Long refundAmountCents;

    private String reason;
}
