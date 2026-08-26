package com.educloud.analytics.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyFinanceItem {
    private String month;
    private Double income;
    private Double refund;
    private Double net;
}
