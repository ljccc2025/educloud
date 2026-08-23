package com.educloud.course.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.dto.response.AdminCourseResponse;
import com.educloud.course.service.CourseService;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 23：管理端课程列表端点映射与权限测试（GET /api/v1/admin/courses）。
 *
 * <p>依据：规格 §6 管理查询语义与任务 23 —— 管理全状态课程列表需 course:audit；
 * 无该权限 → 403 COURSE_ACCESS_DENIED。@WebMvcTest + mock service 与 JwtDecoder
 * （参照 CourseLifecycleControllerTest 模式）。</p>
 */
@WebMvcTest(controllers = CourseAdminController.class)
@Import({SecurityConfig.class, CourseAdminControllerTest.TestInfrastructure.class})
class CourseAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

    @Test
    void listRequiresCourseAuditPermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:update")));

        mockMvc.perform(get("/api/v1/admin/courses")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
        verify(courseService, never()).listAdminCourses(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void listReturnsPageForAuthorizedAdmin() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:audit")));
        when(courseService.listAdminCourses(1001L, 1, 20, null))
                .thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/admin/courses")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(courseService).listAdminCourses(1001L, 1, 20, null);
    }

    @Test
    void listPassesLifecycleStatusFilter() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:audit")));
        when(courseService.listAdminCourses(1001L, 2, 50, "OFFLINE"))
                .thenReturn(PageResponse.of(List.of(), 2, 50, 0));

        mockMvc.perform(get("/api/v1/admin/courses")
                        .param("page", "2")
                        .param("pageSize", "50")
                        .param("lifecycleStatus", "OFFLINE")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        verify(courseService).listAdminCourses(1001L, 2, 50, "OFFLINE");
    }

    private static Jwt token(String subject, List<String> permissions) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("sid", "session-" + subject)
                .claim("permissions", permissions)
                .build();
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
