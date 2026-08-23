package com.educloud.course.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.dto.response.CourseStudentResponse;
import com.educloud.course.dto.response.EnrollmentResponse;
import com.educloud.course.dto.response.MyCourseResponse;
import com.educloud.course.service.EnrollmentService;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 13：选课控制器端点映射与权限测试。
 *
 * <p>依据：规格 §6 —— POST /courses/{id}/enrollments 需 course:enroll（幂等返回现状）；
 * GET /me/enrollments 登录即可（学生「我的课程」）；GET /courses/{id}/students 需
 * course:student:read（归属校验在服务层 TeacherAccessGuard）。
 * 参照 CourseTeacherControllerTest 模式：@WebMvcTest + mock service 与 JwtDecoder，
 * 验证 @PreAuthorize 在安全链中的行为（缺权限 → 403 COURSE_ACCESS_DENIED）。</p>
 */
@WebMvcTest(controllers = EnrollmentController.class)
@Import({SecurityConfig.class, EnrollmentControllerTest.TestInfrastructure.class})
class EnrollmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnrollmentService enrollmentService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

    @Test
    void enrollRequiresCourseEnrollPermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("5001", List.of("course:student:read")));

        mockMvc.perform(post("/api/v1/courses/101/enrollments")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    @Test
    void enrollReturnsIdempotentEnrollmentResponse() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("5001", List.of("course:enroll")));
        when(enrollmentService.enroll(101L, 5001L))
                .thenReturn(new EnrollmentResponse(
                        "501", "101", "5001", "FREE", "ACTIVE",
                        LocalDateTime.of(2026, 8, 23, 10, 30)));

        mockMvc.perform(post("/api/v1/courses/101/enrollments")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.enrollmentId").value("501"))
                .andExpect(jsonPath("$.data.courseId").value("101"))
                .andExpect(jsonPath("$.data.studentId").value("5001"))
                .andExpect(jsonPath("$.data.source").value("FREE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(enrollmentService).enroll(101L, 5001L);
    }

    @Test
    void myCoursesReturnsStudentEnrollmentPage() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("5001", List.of()));
        when(enrollmentService.myCourses(5001L, 1, 20))
                .thenReturn(PageResponse.of(List.of(new MyCourseResponse(
                        "101", "高等数学", "http://bucket/cover-88", "ACTIVE",
                        LocalDateTime.of(2026, 8, 23, 10, 30))), 1, 20, 1));

        mockMvc.perform(get("/api/v1/me/enrollments")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].courseId").value("101"))
                .andExpect(jsonPath("$.data.items[0].title").value("高等数学"))
                .andExpect(jsonPath("$.data.items[0].coverUrl").value("http://bucket/cover-88"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(enrollmentService).myCourses(5001L, 1, 20);
    }

    @Test
    void studentsRequiresCourseStudentReadPermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:enroll")));

        mockMvc.perform(get("/api/v1/courses/101/students")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    @Test
    void studentsReturnsStudentPageForOwningTeacher() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:student:read")));
        when(enrollmentService.listStudents(eq(101L), eq(1001L), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(new CourseStudentResponse(
                        "5001", null, LocalDateTime.of(2026, 8, 23, 10, 30))), 1, 20, 1));

        mockMvc.perform(get("/api/v1/courses/101/students")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].studentId").value("5001"))
                .andExpect(jsonPath("$.data.items[0].displayName").value(nullValue()));

        verify(enrollmentService).listStudents(eq(101L), eq(1001L), anyInt(), anyInt());
    }

    /** 建议：缺权限的 3 端点统一负向参数化（course:enroll / course:student:read）。 */
    @ParameterizedTest(name = "{0} {1} without required permission -> 403")
    @MethodSource("protectedEndpoints")
    void protectedEndpointsRequireTheirPermission(String method, String url, String permission,
            String grantedPermission) throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of(grantedPermission)));

        var request = request(HttpMethod.valueOf(method), url).header("Authorization", "Bearer test-token");
        if ("POST".equals(method)) {
            request = request.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/courses/101/enrollments", "course:enroll", "course:student:read"),
                Arguments.of("GET", "/api/v1/courses/101/students", "course:student:read", "course:enroll"));
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