package com.educloud.analytics.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceOverviewResponse {

    private FinanceStats stats;
    private List<MonthlyFinanceItem> monthly;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceStats {
        private Double totalGmv;
        private Double pendingSettlement;
        private Double totalRefund;
        private Double refundRate;
        private Double avgOrderAmount;
    }
}
