package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.admin.FinanceOverviewResponse;
import com.educloud.analytics.service.FinanceAnalyticsService;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端财务大屏接口")
@RestController
@RequestMapping("/api/v1/analytics/admin/finance")
@RequiredArgsConstructor
public class FinanceAnalyticsController {

    private final FinanceAnalyticsService financeAnalyticsService;
    private final ApiResponseFactory responses;

    @Operation(summary = "获取管理端财务中心营收与月度流水走势")
    @GetMapping("/overview")
    public ApiResponse<FinanceOverviewResponse> getFinanceOverview() {
        return responses.success(financeAnalyticsService.getFinanceOverview());
    }
}
