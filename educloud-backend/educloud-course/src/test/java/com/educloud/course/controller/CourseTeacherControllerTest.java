package com.educloud.course.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.dto.request.CourseCreateRequest;
import com.educloud.course.dto.request.CourseDraftUpdateRequest;
import com.educloud.course.dto.response.CourseDraftResponse;
import com.educloud.course.service.CourseService;
import com.educloud.course.service.CourseVersionService;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 8：教师课程控制器（CourseTeacherController）端点映射与权限测试。
 *
 * <p>依据：任务 8 —— POST /courses 需 course:create；GET /teacher/courses/{id}/draft、
 * POST /courses/{id}/drafts、PUT /course-drafts/{versionId} 需 course:update。
 * @WebMvcTest + mock service 与 JwtDecoder（参照 CategoryControllerTest 模式），
 * JwtDecoder 直接返回带 permissions claim 的 Jwt 对象，验证 @PreAuthorize 方法安全
 * 在安全链中的行为（无 course:create → 403 COURSE_ACCESS_DENIED）。</p>
 */
@WebMvcTest(controllers = CourseTeacherController.class)
@Import({SecurityConfig.class, CourseTeacherControllerTest.TestInfrastructure.class})
class CourseTeacherControllerTest {

    private static final String CREATE_BODY = """
            {"title":"Java 入门","subtitle":"从零开始","description":"基础语法",
             "coverFileId":"77","level":"BEGINNER","price":199.00,"currency":"CNY","categoryId":"5"}
            """;

    private static final String UPDATE_BODY = """
            {"title":"新标题","subtitle":"新副标题","description":"新描述",
             "coverFileId":"88","level":"INTERMEDIATE","price":299.00,"currency":"USD","categoryId":"8"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private CourseVersionService courseVersionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

    @Test
    void createCourseRequiresCourseCreatePermission() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:update")));

        mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    @Test
    void createCourseReturnsCreatedDraft() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:create", "course:update")));
        when(courseService.createCourse(eq(1001L), any(CourseCreateRequest.class)))
                .thenReturn(draftResponse("101", "301", 1, "Java 入门", "199.00"));

        mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.courseId").value("101"))
                .andExpect(jsonPath("$.data.versionId").value("301"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.title").value("Java 入门"))
                .andExpect(jsonPath("$.data.price").value("199.00"))
                .andExpect(jsonPath("$.data.versionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.teachers[0].teacherId").value("1001"))
                .andExpect(jsonPath("$.data.teachers[0].role").value("OWNER"));

        verify(courseService).createCourse(eq(1001L), any(CourseCreateRequest.class));
    }

    @Test
    void createCourseRejectsInvalidBodyWith400() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:create")));

        mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","level":"EXPERT","price":-1,"currency":"CNY","categoryId":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void currentDraftReturnsDraftForOwnerTeacher() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:update")));
        when(courseVersionService.getCurrentDraft(101L, 1001L))
                .thenReturn(draftResponse("101", "301", 1, "草稿标题", "199.00"));

        mockMvc.perform(get("/api/v1/teacher/courses/101/draft")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.courseId").value("101"))
                .andExpect(jsonPath("$.data.title").value("草稿标题"));

        verify(courseVersionService).getCurrentDraft(101L, 1001L);
    }

    @Test
    void createDraftFromPublishedOrRejectedRequiresCourseUpdate() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:update")));
        when(courseVersionService.createDraftFromPublishedOrRejected(101L, 1001L))
                .thenReturn(draftResponse("101", "302", 2, "复制标题", "199.00"));

        mockMvc.perform(post("/api/v1/courses/101/drafts")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.versionId").value("302"))
                .andExpect(jsonPath("$.data.versionNo").value(2));

        verify(courseVersionService).createDraftFromPublishedOrRejected(101L, 1001L);
    }

    @Test
    void updateDraftRequiresCourseUpdate() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:update")));
        when(courseVersionService.updateDraft(eq(301L), eq(1001L), any(CourseDraftUpdateRequest.class)))
                .thenReturn(draftResponse("101", "301", 1, "新标题", "299.00"));

        mockMvc.perform(put("/api/v1/course-drafts/301")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.title").value("新标题"))
                .andExpect(jsonPath("$.data.price").value("299.00"));

        verify(courseVersionService).updateDraft(eq(301L), eq(1001L), any(CourseDraftUpdateRequest.class));
    }

    /** 建议 3：course:update 三端点负向权限（仅有 course:create 无 course:update → 403）。 */
    @ParameterizedTest(name = "{0} {1} without course:update -> 403")
    @MethodSource("courseUpdateEndpoints")
    void courseUpdateEndpointsRequireCourseUpdatePermission(
            String method, String url, String body) throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:create")));

        var request = request(HttpMethod.valueOf(method), url).header("Authorization", "Bearer test-token");
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    static Stream<Arguments> courseUpdateEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/teacher/courses/101/draft", null),
                Arguments.of("POST", "/api/v1/courses/101/drafts", null),
                Arguments.of("PUT", "/api/v1/course-drafts/301", UPDATE_BODY));
    }

    private static Jwt token(String subject, List<String> permissions) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("sid", "session-" + subject)
                .claim("permissions", permissions)
                .build();
    }

    private static CourseDraftResponse draftResponse(
            String courseId, String versionId, int versionNo, String title, String price) {
        return new CourseDraftResponse(
                courseId, versionId, versionNo, title, "副标题", "描述", "77",
                "BEGINNER", price, "CNY", "5", "DRAFT", "DRAFT",
                List.of(new CourseDraftResponse.Teacher("1001", "OWNER")));
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
