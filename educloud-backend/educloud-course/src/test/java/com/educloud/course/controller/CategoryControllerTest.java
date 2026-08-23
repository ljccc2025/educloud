package com.educloud.course.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.dto.response.CategoryResponse;
import com.educloud.course.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 7：GET /api/v1/categories 控制器测试（匿名可达 + 树结构序列化）。
 *
 * <p>依据：任务 7 —— Gateway AccessPolicy.PUBLIC_READ 匿名放行后无 Authorization 头转发，
 * Course 服务内必须 permitAll 该端点（参照 user /api/v1/platform-config/public 处理）。
 * ApiResponseFactory 使用真实 Bean（TestInfrastructure 提供 Clock/RequestIdPolicy/
 * RequestContextAccessor，仿 educloud-file FileStorageControllerTest），仅 mock
 * CategoryService 与安全链外部依赖（JwtDecoder/CourseProperties）。</p>
 */
@WebMvcTest(controllers = CategoryController.class)
@Import({SecurityConfig.class, CategoryControllerTest.TestInfrastructure.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

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
                .andExpect(jsonPath("$.data[1].children").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-21T10:00:00Z"), ZoneOffset.UTC);
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
