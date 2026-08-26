package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.admin.DashboardStatsResponse;
import com.educloud.analytics.dto.response.admin.UserGrowthItem;
import com.educloud.analytics.entity.AnalyticsRebuildTaskEntity;
import com.educloud.analytics.enums.RebuildStage;
import com.educloud.analytics.enums.RebuildStatus;
import com.educloud.analytics.service.AdminAnalyticsService;
import com.educloud.analytics.service.AggregationRebuildService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TestInfrastructure.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAnalyticsService adminAnalyticsService;

    @MockBean
    private AggregationRebuildService rebuildService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("测试获取管理端概览大屏统计 GET /api/v1/analytics/admin/stats")
    void testGetStats() throws Exception {
        when(adminAnalyticsService.getDashboardStats()).thenReturn(
                DashboardStatsResponse.builder().totalUsers(28450).userGrowthRate(12.8).totalCourses(156).totalRevenue(1584200.0).activeLives(3).build()
        );

        mockMvc.perform(get("/api/v1/analytics/admin/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalUsers").value(28450))
                .andExpect(jsonPath("$.data.activeLives").value(3));
    }

    @Test
    @DisplayName("测试获取管理端增长趋势 GET /api/v1/analytics/admin/growth")
    void testGetUserGrowth() throws Exception {
        when(adminAnalyticsService.getUserGrowth()).thenReturn(
                List.of(UserGrowthItem.builder().date("08-26").users(28450).courses(156).build())
        );

        mockMvc.perform(get("/api/v1/analytics/admin/growth").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].date").value("08-26"));
    }

    @Test
    @DisplayName("测试触发全量指标重算 POST /api/v1/analytics/admin/rebuild")
    void testTriggerRebuild() throws Exception {
        when(rebuildService.triggerRebuild(anyString())).thenReturn("REBUILD_TASK_001");

        mockMvc.perform(post("/api/v1/analytics/admin/rebuild")
                        .header("X-Operator", "admin")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskNo").value("REBUILD_TASK_001"));
    }

    @Test
    @DisplayName("测试查询重算进度 GET /api/v1/analytics/admin/rebuild/{taskNo}")
    void testGetRebuildProgress() throws Exception {
        when(rebuildService.getTaskProgress("REBUILD_TASK_001")).thenReturn(
                AnalyticsRebuildTaskEntity.builder().taskNo("REBUILD_TASK_001").status(RebuildStatus.SUCCESS).stage(RebuildStage.COMPLETED).totalItems(500).processedItems(500).build()
        );

        mockMvc.perform(get("/api/v1/analytics/admin/rebuild/REBUILD_TASK_001").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskNo").value("REBUILD_TASK_001"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
}
