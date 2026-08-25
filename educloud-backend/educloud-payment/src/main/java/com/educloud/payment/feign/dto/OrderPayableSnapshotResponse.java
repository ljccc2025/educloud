package com.educloud.payment.feign.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPayableSnapshotResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    private String orderNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long studentId;

    private String status;
    private BigDecimal payableAmount;
    private String currency;
    private LocalDateTime expiresAt;
}
