package com.educloud.course.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.service.CourseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 10：课程生命周期控制器端点映射与权限测试。
 *
 * <p>依据：规格 §6 —— POST /courses/{id}/offline 需 course:offline、POST /courses/{id}/republish
 * 需 course:republish、POST /courses/{id}/archive 需 course:archive；归属校验在服务层
 * （TeacherAccessGuard），控制器只做权限码门禁。@WebMvcTest + mock service 与 JwtDecoder
 * （参照 CourseTeacherControllerTest 模式）：JwtDecoder 直接返回带 permissions claim 的
 * Jwt，验证 @PreAuthorize 方法安全在安全链中的行为（无对应权限 → 403 COURSE_ACCESS_DENIED）。</p>
 */
@WebMvcTest(controllers = CourseLifecycleController.class)
@Import({SecurityConfig.class, CourseLifecycleControllerTest.TestInfrastructure.class})
class CourseLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

    @Test
    void offlineRequiresCourseOfflinePermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:update")));

        mockMvc.perform(post("/api/v1/courses/101/offline")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
        verify(courseService, never()).offline(101L, 1001L);
    }

    @Test
    void republishRequiresCourseRepublishPermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:offline")));

        mockMvc.perform(post("/api/v1/courses/101/republish")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
        verify(courseService, never()).republish(101L, 1001L);
    }

    @Test
    void archiveRequiresCourseArchivePermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:offline")));

        mockMvc.perform(post("/api/v1/courses/101/archive")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
        verify(courseService, never()).archive(101L, 1001L);
    }

    /** 生命周期端点全缺对应权限 → 403（参数化覆盖三端点）。 */
    @ParameterizedTest(name = "{0} without permission -> 403")
    @MethodSource("lifecycleEndpoints")
    void lifecycleEndpointsRequireTheirPermission(String url) throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:create")));

        mockMvc.perform(post(url).header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    static Stream<Arguments> lifecycleEndpoints() {
        return Stream.of(
                Arguments.of("/api/v1/courses/101/offline"),
                Arguments.of("/api/v1/courses/101/republish"),
                Arguments.of("/api/v1/courses/101/archive"));
    }

    @Test
    void offlineReturnsSuccessForAuthorizedTeacher() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:offline", "course:republish", "course:archive")));

        mockMvc.perform(post("/api/v1/courses/101/offline")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(courseService).offline(101L, 1001L);
    }

    @Test
    void republishReturnsSuccessForAuthorizedTeacher() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:offline", "course:republish", "course:archive")));

        mockMvc.perform(post("/api/v1/courses/101/republish")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(courseService).republish(101L, 1001L);
    }

    @Test
    void archiveReturnsSuccessForAuthorizedTeacher() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:offline", "course:republish", "course:archive")));

        mockMvc.perform(post("/api/v1/courses/101/archive")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(courseService).archive(101L, 1001L);
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
