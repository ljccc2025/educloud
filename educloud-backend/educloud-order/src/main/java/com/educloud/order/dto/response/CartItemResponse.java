package com.educloud.order.dto.response;

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
public class CartItemResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    private String courseTitle;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverFileId;

    private BigDecimal unitPrice;
    private Boolean selected;
    private Boolean isOnSale;
    private LocalDateTime createdAt;
}
