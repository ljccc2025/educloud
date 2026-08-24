package com.educloud.order.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    private String courseTitleSnapshot;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverFileIdSnapshot;

    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;
    private String fulfillmentStatus;
}
