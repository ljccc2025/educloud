package com.educloud.payment.dto.request;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.TradeType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashierPayRequest {

    @NotNull(message = "订单ID不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @NotNull(message = "支付渠道不能为空")
    private PaymentChannel channelCode;

    private TradeType tradeType;

    private String subject;

    private String clientIp;
}
