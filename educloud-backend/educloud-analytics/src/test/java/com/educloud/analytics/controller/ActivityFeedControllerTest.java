package com.educloud.analytics.controller;

import com.educloud.analytics.entity.ActivityFeedEntity;
import com.educloud.analytics.service.ActivityFeedService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ActivityFeedController 单测：按角色过滤、空结果、ISO 时间格式、limit 钳制。
 */
@WebMvcTest(ActivityFeedController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TestInfrastructure.class)
class ActivityFeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityFeedService activityFeedService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static ActivityFeedEntity entity(
            long id, String actorId, String actorRole, String actionType,
            String targetType, String targetId, String targetTitle,
            String extraJson, LocalDateTime occurredAt) {
        return ActivityFeedEntity.builder()
                .id(id)
                .actorId(actorId)
                .actorRole(actorRole)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .targetTitle(targetTitle)
                .extraJson(extraJson)
                .sourceEvent("EVT_" + id)
                .occurredAt(occurredAt)
                .build();
    }

    @Test
    @DisplayName("学员动态：按当前登录学员 + STUDENT 角色过滤")
    void testStudentActivitiesFilteredByCurrentUserAndRole() throws Exception {
        when(activityFeedService.listActivities(eq("stu_1001"), eq("STUDENT"), eq(10))).thenReturn(
                List.of(entity(1L, "stu_1001", "STUDENT", "ENROLLED", "COURSE", "course_101",
                        "Spring Cloud 微服务", null, LocalDateTime.of(2026, 8, 27, 10, 30)))
        );

        mockMvc.perform(get("/api/v1/analytics/student/activities")
                        .header("X-User-Id", "stu_1001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].actionType").value("ENROLLED"))
                .andExpect(jsonPath("$.data[0].action").value("你报名了《Spring Cloud 微服务》"))
                .andExpect(jsonPath("$.data[0].targetTitle").value("Spring Cloud 微服务"));

        verify(activityFeedService).listActivities(eq("stu_1001"), eq("STUDENT"), eq(10));
    }

    @Test
    @DisplayName("教师动态：按当前登录教师 + TEACHER 角色过滤，文案含扩展分数")
    void testTeacherActivitiesFilteredByCurrentUserAndRole() throws Exception {
        when(activityFeedService.listActivities(eq("teacher_01"), eq("TEACHER"), eq(10))).thenReturn(
                List.of(entity(2L, "teacher_01", "TEACHER", "STUDENT_ENROLLED", "COURSE", "course_101",
                        "Vue 3 中台", "{\"studentId\":\"stu_1002\"}", LocalDateTime.of(2026, 8, 27, 11, 0)))
        );

        mockMvc.perform(get("/api/v1/analytics/teacher/activities")
                        .header("X-Teacher-Id", "teacher_01")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].actionType").value("STUDENT_ENROLLED"))
                .andExpect(jsonPath("$.data[0].action").value("有学员报名了《Vue 3 中台》"))
                .andExpect(jsonPath("$.data[0].extra.studentId").value("stu_1002"));

        verify(activityFeedService).listActivities(eq("teacher_01"), eq("TEACHER"), eq(10));
    }

    @Test
    @DisplayName("无动态返回空数组")
    void testEmptyResultReturnsEmptyArray() throws Exception {
        when(activityFeedService.listActivities(anyString(), anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/student/activities")
                        .header("X-User-Id", "stu_9999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("时间返回 ISO timestamp（可被前端 Date 解析，避免 Invalid Date）")
    void testTimestampIsIsoFormat() throws Exception {
        when(activityFeedService.listActivities(anyString(), anyString(), anyInt())).thenReturn(
                List.of(entity(3L, "stu_1001", "STUDENT", "ASSIGNMENT_GRADED", "ASSIGNMENT", "asg_3001",
                        "微服务模块打包", "{\"score\":95}", LocalDateTime.of(2026, 8, 27, 14, 30, 5)))
        );

        mockMvc.perform(get("/api/v1/analytics/student/activities")
                        .header("X-User-Id", "stu_1001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].timestamp").value("2026-08-27T14:30:05"))
                .andExpect(jsonPath("$.data[0].action").value("你的《微服务模块打包》作业已批改：95 分"))
                .andExpect(jsonPath("$.data[0].extra.score").value(95));
    }

    @Test
    @DisplayName("limit 钳制：超过上限 50 时按 50 查询，默认 10")
    void testLimitClampedToMax() throws Exception {
        when(activityFeedService.listActivities(anyString(), anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/student/activities")
                        .param("limit", "999")
                        .header("X-User-Id", "stu_1001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(activityFeedService).listActivities(eq("stu_1001"), eq("STUDENT"), eq(50));
    }
}
