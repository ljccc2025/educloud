package com.educloud.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartSummaryResponse {
    private List<CartItemResponse> items;
    private Integer totalCount;
    private Integer selectedCount;
    private BigDecimal selectedAmount;
}
