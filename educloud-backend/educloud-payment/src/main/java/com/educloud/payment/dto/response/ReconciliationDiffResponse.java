package com.educloud.payment.dto.response;

import com.educloud.payment.enums.DiffType;
import com.educloud.payment.enums.ResolveAction;
import com.educloud.payment.enums.ResolveStatus;
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
public class ReconciliationDiffResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    private DiffType diffType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentOrderId;

    private String channelTradeNo;

    private Long localAmountCents;

    private Long channelAmountCents;

    private String localStatus;

    private String channelStatus;

    private ResolveStatus resolveStatus;

    private ResolveAction resolveAction;

    private String resolveRemark;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long resolvedBy;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;
}
