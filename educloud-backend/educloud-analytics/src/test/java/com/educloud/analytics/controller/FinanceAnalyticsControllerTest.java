package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.admin.FinanceOverviewResponse;
import com.educloud.analytics.service.FinanceAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TestInfrastructure.class)
class FinanceAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceAnalyticsService financeAnalyticsService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("测试获取财务概览大屏数据 GET /api/v1/analytics/admin/finance/overview")
    void testGetFinanceOverview() throws Exception {
        FinanceOverviewResponse.FinanceStats stats = FinanceOverviewResponse.FinanceStats.builder()
                .totalGmv(1584200.0)
                .pendingSettlement(45200.0)
                .totalRefund(32800.0)
                .refundRate(2.07)
                .avgOrderAmount(298.5)
                .build();

        when(financeAnalyticsService.getFinanceOverview()).thenReturn(
                FinanceOverviewResponse.builder().stats(stats).monthly(List.of()).build()
        );

        mockMvc.perform(get("/api/v1/analytics/admin/finance/overview").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.stats.totalGmv").value(1584200.0))
                .andExpect(jsonPath("$.data.stats.refundRate").value(2.07));
    }
}
