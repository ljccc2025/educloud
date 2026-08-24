package com.educloud.order.messaging.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    private String orderNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long studentId;

    private List<Long> courseIds;

    private BigDecimal paidAmount;

    private LocalDateTime paidAt;
}
