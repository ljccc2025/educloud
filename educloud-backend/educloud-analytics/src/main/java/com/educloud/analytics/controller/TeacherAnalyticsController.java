package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.teacher.*;
import com.educloud.analytics.security.JwtSecurityUtils;
import com.educloud.analytics.service.TeacherAnalyticsService;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "教师端学习分析接口")
@RestController
@RequestMapping("/api/v1/analytics/teacher")
@RequiredArgsConstructor
public class TeacherAnalyticsController {

    private final TeacherAnalyticsService teacherAnalyticsService;
    private final ApiResponseFactory responses;

    @Operation(summary = "获取教师端概览四大核心指标")
    @GetMapping("/stats")
    public ApiResponse<TeacherStatsResponse> getStats(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        String teacherId = JwtSecurityUtils.extractTeacherId(jwt, request);
        return responses.success(teacherAnalyticsService.getStats(teacherId));
    }

    @Operation(summary = "获取教师端近6个月学员报名趋势")
    @GetMapping("/trend/enrollment")
    public ApiResponse<List<EnrollmentTrendItem>> getEnrollmentTrend(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        String teacherId = JwtSecurityUtils.extractTeacherId(jwt, request);
        return responses.success(teacherAnalyticsService.getEnrollmentTrend(teacherId));
    }

    @Operation(summary = "获取教师端近6个月营收走势")
    @GetMapping("/trend/revenue")
    public ApiResponse<List<RevenueTrendItem>> getRevenueTrend(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        String teacherId = JwtSecurityUtils.extractTeacherId(jwt, request);
        return responses.success(teacherAnalyticsService.getRevenueTrend(teacherId));
    }

    @Operation(summary = "获取教师端课程参与度与完课排行")
    @GetMapping("/engagement")
    public ApiResponse<List<EngagementItem>> getEngagement(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        String teacherId = JwtSecurityUtils.extractTeacherId(jwt, request);
        return responses.success(teacherAnalyticsService.getEngagement(teacherId));
    }

    @Operation(summary = "获取教师端实时学员动态流")
    @GetMapping("/activities")
    public ApiResponse<List<TeacherActivityItem>> getActivities(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        String teacherId = JwtSecurityUtils.extractTeacherId(jwt, request);
        return responses.success(teacherAnalyticsService.getActivities(teacherId));
    }
}
