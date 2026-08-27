package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.teacher.*;
import com.educloud.analytics.service.TeacherAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeacherAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TestInfrastructure.class)
class TeacherAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherAnalyticsService teacherAnalyticsService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 直设 JWT 认证上下文（@WebMvcTest addFilters=false 时 SecurityMockMvcRequestPostProcessors 不生效）。 */
    private static void authenticateAs(String subject) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("测试获取教师概览统计接口 GET /api/v1/analytics/teacher/stats")
    void testGetStats() throws Exception {
        when(teacherAnalyticsService.getStats(anyString())).thenReturn(
                TeacherStatsResponse.builder().totalCourses(12).totalStudents(3420).totalRevenue(128500.0).completionRate(78.5).build()
        );

        authenticateAs("teacher_01");

        mockMvc.perform(get("/api/v1/analytics/teacher/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalCourses").value(12))
                .andExpect(jsonPath("$.data.totalStudents").value(3420));
    }

    @Test
    @DisplayName("测试获取教师报名趋势接口 GET /api/v1/analytics/teacher/trend/enrollment")
    void testGetEnrollmentTrend() throws Exception {
        when(teacherAnalyticsService.getEnrollmentTrend(anyString())).thenReturn(
                List.of(EnrollmentTrendItem.builder().month("2026-08").enrollments(500).build())
        );

        authenticateAs("teacher_01");

        mockMvc.perform(get("/api/v1/analytics/teacher/trend/enrollment")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].month").value("2026-08"))
                .andExpect(jsonPath("$.data[0].enrollments").value(500));
    }

    @Test
    @DisplayName("测试获取教师热门课程排行 GET /api/v1/analytics/teacher/engagement")
    void testGetEngagement() throws Exception {
        when(teacherAnalyticsService.getEngagement(anyString())).thenReturn(
                List.of(EngagementItem.builder().courseId("c_1").courseTitle("Spring Cloud").totalEnrollments(100).completionRate(85.0).build())
        );

        authenticateAs("teacher_01");

        mockMvc.perform(get("/api/v1/analytics/teacher/engagement")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].courseTitle").value("Spring Cloud"));
    }
}
