package com.educloud.payment.dto.response;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.ReconciliationBatchStatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationBatchResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String batchNo;

    private LocalDate reconcileDate;

    private PaymentChannel channelCode;

    private Integer totalCount;

    private Long totalAmountCents;

    private Integer diffCount;

    private ReconciliationBatchStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
