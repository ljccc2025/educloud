package com.educloud.order.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String orderNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long studentId;

    private String status;
    private BigDecimal originalAmount;
    private BigDecimal payableAmount;
    private String currency;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private List<OrderItemResponse> items;
    private Long countdownSeconds;
}
