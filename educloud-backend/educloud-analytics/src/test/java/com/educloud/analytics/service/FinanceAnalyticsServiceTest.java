package com.educloud.analytics.service;

import com.educloud.analytics.dto.response.admin.FinanceOverviewResponse;
import com.educloud.analytics.mapper.DailyFinanceMetricsMapper;
import com.educloud.analytics.service.impl.FinanceAnalyticsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceAnalyticsServiceTest {

    @Mock
    private DailyFinanceMetricsMapper financeMetricsMapper;

    @InjectMocks
    private FinanceAnalyticsServiceImpl financeAnalyticsService;

    @Test
    @DisplayName("测试财务总览统计与近 6 个月月度收支双柱数据")
    void testGetFinanceOverview() {
        when(financeMetricsMapper.selectFinanceSummary()).thenReturn(Map.of(
                "total_gross_cents", 180000000L,
                "total_refund_cents", 3600000L,
                "total_orders", 6000
        ));
        when(financeMetricsMapper.selectMonthlyFinance(any(), any())).thenReturn(List.of(
                Map.of("month", "2026-08", "total_gross_cents", 22000000L, "total_refund_cents", 440000L)
        ));

        FinanceOverviewResponse resp = financeAnalyticsService.getFinanceOverview();

        assertThat(resp.getStats().getTotalGmv()).isEqualTo(1800000.0);
        assertThat(resp.getStats().getTotalRefund()).isEqualTo(36000.0);
        assertThat(resp.getStats().getRefundRate()).isEqualTo(2.0);
        assertThat(resp.getStats().getAvgOrderAmount()).isEqualTo(300.0);

        assertThat(resp.getMonthly()).hasSize(6);
        assertThat(resp.getMonthly().get(5).getIncome()).isEqualTo(220000.0);
        assertThat(resp.getMonthly().get(5).getRefund()).isEqualTo(4400.0);
        assertThat(resp.getMonthly().get(5).getNet()).isEqualTo(215600.0);
    }
}
