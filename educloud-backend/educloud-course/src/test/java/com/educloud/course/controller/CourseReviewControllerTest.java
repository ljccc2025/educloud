package com.educloud.course.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.dto.response.CourseReviewResponse;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.service.CourseReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 14：课程评价控制器端点映射与校验测试。
 *
 * <p>依据：规格 §6 —— POST /courses/{id}/reviews：登录即可（服务层校验 ACTIVE 选课），
 * rating 1-5 必填（@Valid → 400 VALIDATION_FAILED），未选课 403 NOT_ENROLLED；
 * DELETE /course-reviews/{id}：管理角色由服务层按 JWT roles claim 判定（无 review
 * 专用权限码且安全链只映射 permissions claim，故不用 @PreAuthorize）→ 非管理角色
 * 403 COURSE_ACCESS_DENIED。参照 CourseTeacherControllerTest 模式：@WebMvcTest +
 * mock service 与 JwtDecoder。</p>
 */
@WebMvcTest(controllers = CourseReviewController.class)
@Import({SecurityConfig.class, CourseReviewControllerTest.TestInfrastructure.class})
class CourseReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseReviewService reviewService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

    @Test
    void postReviewRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/courses/101/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "rating={0} -> 400 VALIDATION_FAILED")
    @ValueSource(strings = {"0", "6", "-1"})
    void postReviewRejectsOutOfRangeRatingWith400(String rating) throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("5001", List.of()));

        mockMvc.perform(post("/api/v1/courses/101/reviews")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + rating + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void postReviewRejectsMissingRatingWith400() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("5001", List.of()));

        mockMvc.perform(post("/api/v1/courses/101/reviews")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void postReviewReturns403ForNotEnrolledStudent() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("5001", List.of()));
        when(reviewService.upsert(eq(101L), eq(5001L), any()))
                .thenThrow(new BusinessException(CourseErrorCode.NOT_ENROLLED));

        mockMvc.perform(post("/api/v1/courses/101/reviews")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"好课\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
    }

    @Test
    void postReviewReturnsUpsertedReview() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("5001", List.of()));
        when(reviewService.upsert(eq(101L), eq(5001L), any()))
                .thenReturn(new CourseReviewResponse(
                        "501", "5001", 5, "好课", "VISIBLE",
                        LocalDateTime.of(2026, 8, 24, 10, 0),
                        LocalDateTime.of(2026, 8, 24, 10, 0)));

        mockMvc.perform(post("/api/v1/courses/101/reviews")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"好课\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("501"))
                .andExpect(jsonPath("$.data.studentId").value("5001"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("好课"))
                .andExpect(jsonPath("$.data.status").value("VISIBLE"));
    }

    @Test
    void deleteReviewRejectsNonAdminWith403() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("30001", List.of()));
        when(reviewService.hide(eq(501L), eq(30001L), any()))
                .thenThrow(new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED));

        mockMvc.perform(delete("/api/v1/course-reviews/501")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    @Test
    void deleteReviewReturnsHiddenReview() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("30001", List.of()));
        when(reviewService.hide(eq(501L), eq(30001L), any()))
                .thenReturn(new CourseReviewResponse(
                        "501", "5001", 5, "好课", "HIDDEN",
                        LocalDateTime.of(2026, 8, 24, 10, 0),
                        LocalDateTime.of(2026, 8, 24, 12, 0)));

        mockMvc.perform(delete("/api/v1/course-reviews/501")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("501"))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    private static Jwt token(String subject, List<String> permissions) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("sid", "session-" + subject)
                .claim("permissions", permissions)
                .claim("roles", List.of("STUDENT"))
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
