package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.dto.response.CategoryResponse;
import com.educloud.course.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 7：GET /api/v1/categories 控制器测试（匿名可达 + 树结构序列化）。
 * 依据：任务 7 —— Gateway AccessPolicy.PUBLIC_READ 匿名放行后无 Authorization 头转发，
 * Course 服务内必须 permitAll 该端点（参照 user /api/v1/platform-config/public 处理）。
 */
@WebMvcTest(controllers = CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

    @MockBean
    private ApiResponseFactory responses;

    @BeforeEach
    void stubResponses() {
        when(responses.success(any())).thenAnswer(invocation -> new ApiResponse<>(
                "SUCCESS", "OK", invocation.getArgument(0), "req-1",
                Instant.parse("2026-08-21T10:00:00Z")));
    }

    @Test
    void categoriesAreReachableWithoutAuthentication() throws Exception {
        CategoryResponse frontend = new CategoryResponse("2", "前端", "frontend", 1,
                List.of(new CategoryResponse("4", "Spring", "spring", 1, List.of())));
        CategoryResponse backend = new CategoryResponse("1", "后端", "backend", 2, List.of());
        when(categoryService.visibleTree()).thenReturn(List.of(frontend, backend));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].id").value("2"))
                .andExpect(jsonPath("$.data[0].name").value("前端"))
                .andExpect(jsonPath("$.data[0].slug").value("frontend"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data[0].children[0].name").value("Spring"))
                .andExpect(jsonPath("$.data[1].name").value("后端"))
                .andExpect(jsonPath("$.data[1].children").isEmpty());
    }
}
