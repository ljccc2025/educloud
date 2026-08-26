package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.admin.DashboardStatsResponse;
import com.educloud.analytics.dto.response.admin.DistributionsResponse;
import com.educloud.analytics.dto.response.admin.UserGrowthItem;
import com.educloud.analytics.dto.response.teacher.TeacherActivityItem;
import com.educloud.analytics.entity.AnalyticsRebuildTaskEntity;
import com.educloud.analytics.security.JwtSecurityUtils;
import com.educloud.analytics.service.AdminAnalyticsService;
import com.educloud.analytics.service.AggregationRebuildService;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端运营全景与重算接口")
@RestController
@RequestMapping("/api/v1/analytics/admin")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;
    private final AggregationRebuildService rebuildService;
    private final ApiResponseFactory responses;

    @Operation(summary = "获取管理端运营看板四大核心统计指标")
    @GetMapping("/stats")
    public ApiResponse<DashboardStatsResponse> getStats() {
        return responses.success(adminAnalyticsService.getDashboardStats());
    }

    @Operation(summary = "获取管理端用户与课程增长双轴走势")
    @GetMapping("/growth")
    public ApiResponse<List<UserGrowthItem>> getUserGrowth() {
        return responses.success(adminAnalyticsService.getUserGrowth());
    }

    @Operation(summary = "获取管理端课程体系与订单状态分布")
    @GetMapping("/distributions")
    public ApiResponse<DistributionsResponse> getDistributions() {
        return responses.success(adminAnalyticsService.getDistributions());
    }

    @Operation(summary = "获取管理端平台近期操作与业务动态")
    @GetMapping("/activities")
    public ApiResponse<List<TeacherActivityItem>> getRecentActivities() {
        return responses.success(adminAnalyticsService.getRecentActivities());
    }

    @Operation(summary = "一键触发全量指标平滑重算引擎")
    @PostMapping("/rebuild")
    public ApiResponse<Map<String, String>> triggerRebuild(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        String operator = JwtSecurityUtils.extractOperator(jwt, request);
        String taskNo = rebuildService.triggerRebuild(operator);
        return responses.success(Map.of(
                "taskNo", taskNo,
                "message", "指标全量重算任务已提交后台异步执行"
        ));
    }

    @Operation(summary = "查询全量指标重算任务进度")
    @GetMapping("/rebuild/{taskNo}")
    public ApiResponse<AnalyticsRebuildTaskEntity> getRebuildProgress(@PathVariable String taskNo) {
        AnalyticsRebuildTaskEntity task = rebuildService.getTaskProgress(taskNo);
        return responses.success(task);
    }
}
