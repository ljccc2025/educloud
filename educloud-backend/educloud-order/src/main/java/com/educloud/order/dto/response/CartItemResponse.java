package com.educloud.order.dto.response;

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
    private Long id;
    private Long courseId;
    private String courseTitle;
    private Long coverFileId;
    private BigDecimal unitPrice;
    private Boolean selected;
    private Boolean isOnSale;
    private LocalDateTime createdAt;
}
