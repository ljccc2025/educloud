package com.educloud.search.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.search.config.SecurityConfig;
import com.educloud.search.dto.response.IndexTaskProgressResponse;
import com.educloud.search.enums.TaskStatus;
import com.educloud.search.enums.TaskType;
import com.educloud.search.service.IndexRebuildService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SearchAdminController.class)
@Import({SecurityConfig.class, SearchAdminControllerTest.TestInfrastructure.class})
class SearchAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IndexRebuildService indexRebuildService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("具备 search:rebuild 权限的用户触发全量重建并正确提取操作人")
    void testRebuildIndexWithSearchRebuildPermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(tokenWithPermissions("operator_admin", List.of("search:rebuild")));

        IndexTaskProgressResponse progress = IndexTaskProgressResponse.builder()
                .taskNo("SR_20260826120000_1234")
                .indexName("educloud_course_v1724673600000")
                .aliasName("educloud_course_search")
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.RUNNING)
                .totalRecords(100)
                .processedRecords(10)
                .failedRecords(0)
                .progressPercent(10)
                .createdBy("operator_admin")
                .createdAt(LocalDateTime.now())
                .build();

        when(indexRebuildService.triggerFullRebuild("operator_admin")).thenReturn(progress);

        mockMvc.perform(post("/api/v1/search/admin/rebuild-index")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskNo").value("SR_20260826120000_1234"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.createdBy").value("operator_admin"))
                .andExpect(jsonPath("$.data.progressPercent").value(10));

        verify(indexRebuildService).triggerFullRebuild("operator_admin");
    }

    @Test
    @DisplayName("具备 ADMIN 角色的用户触发全量重建正常放行")
    void testRebuildIndexWithAdminRole() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(tokenWithRoles("super_admin", List.of("ADMIN")));

        IndexTaskProgressResponse progress = IndexTaskProgressResponse.builder()
                .taskNo("SR_20260826120000_5678")
                .indexName("educloud_course_v1724673600000")
                .aliasName("educloud_course_search")
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.PENDING)
                .totalRecords(50)
                .processedRecords(0)
                .failedRecords(0)
                .progressPercent(0)
                .createdBy("super_admin")
                .createdAt(LocalDateTime.now())
                .build();

        when(indexRebuildService.triggerFullRebuild("super_admin")).thenReturn(progress);

        mockMvc.perform(post("/api/v1/search/admin/rebuild-index")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskNo").value("SR_20260826120000_5678"));

        verify(indexRebuildService).triggerFullRebuild("super_admin");
    }

    @Test
    @DisplayName("根据任务编号查询重建任务进度")
    void testGetTaskProgress() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(tokenWithPermissions("operator_admin", List.of("search:rebuild")));

        IndexTaskProgressResponse progress = IndexTaskProgressResponse.builder()
                .taskNo("SR_20260826120000_1234")
                .indexName("educloud_course_v1724673600000")
                .aliasName("educloud_course_search")
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.SUCCESS)
                .totalRecords(100)
                .processedRecords(100)
                .failedRecords(0)
                .progressPercent(100)
                .createdBy("operator_admin")
                .createdAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build();

        when(indexRebuildService.getTaskProgress("SR_20260826120000_1234")).thenReturn(progress);

        mockMvc.perform(get("/api/v1/search/admin/tasks/SR_20260826120000_1234")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskNo").value("SR_20260826120000_1234"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.progressPercent").value(100));

        verify(indexRebuildService).getTaskProgress("SR_20260826120000_1234");
    }

    @Test
    @DisplayName("查询最近重建任务列表")
    void testListRecentTasks() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(tokenWithPermissions("operator_admin", List.of("search:rebuild")));

        IndexTaskProgressResponse task1 = IndexTaskProgressResponse.builder()
                .taskNo("SR_01")
                .status(TaskStatus.SUCCESS)
                .build();
        IndexTaskProgressResponse task2 = IndexTaskProgressResponse.builder()
                .taskNo("SR_02")
                .status(TaskStatus.RUNNING)
                .build();

        when(indexRebuildService.listRecentTasks(eq(20))).thenReturn(List.of(task1, task2));

        mockMvc.perform(get("/api/v1/search/admin/tasks")
                        .param("limit", "20")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].taskNo").value("SR_01"))
                .andExpect(jsonPath("$.data[1].taskNo").value("SR_02"));

        verify(indexRebuildService).listRecentTasks(20);
    }

    private static Jwt tokenWithPermissions(String username, List<String> permissions) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "1001")
                .claim("username", username)
                .claim("permissions", permissions)
                .build();
    }

    private static Jwt tokenWithRoles(String username, List<String> roles) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "1001")
                .claim("username", username)
                .claim("roles", roles)
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        RequestIdPolicy requestIdPolicy() {
            return new RequestIdPolicy(UUID::randomUUID);
        }

        @Bean
        RequestContextAccessor requestContextAccessor(RequestIdPolicy requestIdPolicy) {
            return new ServletRequestContextAccessor(requestIdPolicy, null);
        }

        @Bean
        ApiResponseFactory apiResponseFactory(
                RequestContextAccessor requestContextAccessor, Clock clock) {
            return new ApiResponseFactory(requestContextAccessor, clock);
        }
    }
}
