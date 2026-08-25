package com.educloud.payment.dto.request;

import com.educloud.payment.enums.PaymentChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileTriggerRequest {

    @NotNull(message = "对账日期不能为空")
    private LocalDate reconcileDate;

    @NotNull(message = "对账渠道不能为空")
    private PaymentChannel channelCode;
}
