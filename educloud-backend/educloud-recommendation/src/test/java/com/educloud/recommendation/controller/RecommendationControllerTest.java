package com.educloud.recommendation.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.recommendation.config.SecurityConfig;
import com.educloud.recommendation.dto.response.RecommendationResponse;
import com.educloud.recommendation.service.FeedbackService;
import com.educloud.recommendation.service.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 推荐与反馈接口 Web 切片测试（M13 任务 7）。
 *
 * <p>模式对齐 educloud-course InternalCourseControllerTest / CategoryControllerTest：
 * @WebMvcTest + @Import(SecurityConfig + TestInfrastructure) + @MockBean JwtDecoder
 * （替换真实 JwtDecoder，避免启动加载 JWKS 文件；Security 过滤链生效，匿名 POST 走
 * 自定义 401 入口点）。ApiResponseFactory 使用真实 Bean（TestInfrastructure 提供
 * Clock/RequestIdPolicy/RequestContextAccessor）。400 语义由 common
 * GlobalExceptionHandler（AutoConfiguration 注册）统一映射。</p>
 */
@WebMvcTest(controllers = RecommendationController.class)
@Import({SecurityConfig.class, RecommendationControllerTest.TestInfrastructure.class})
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/v1/recommendations?context=home 匿名可达，返回 configVersion")
    void homeContextIsReachableWithoutAuthentication() throws Exception {
        when(feedbackService.dislikedCourseIds(isNull())).thenReturn(Set.of());
        when(recommendationService.recommend(isNull(), isNull(), eq(10), anySet()))
                .thenReturn(RecommendationResponse.builder()
                        .configVersion(1)
                        .items(List.of())
                        .build());

        mockMvc.perform(get("/api/v1/recommendations").param("context", "home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.configVersion").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/recommendations?context=course 缺 courseId → 400")
    void courseContextRequiresCourseId() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations").param("context", "course"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_COURSE_ID_REQUIRED"));

        verifyNoInteractions(recommendationService, feedbackService);
    }

    @Test
    @DisplayName("GET /api/v1/recommendations?context=xxx 非法上下文 → 400")
    void unknownContextIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations").param("context", "xxx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_CONTEXT_INVALID"));
    }

    @Test
    @DisplayName("POST /api/v1/recommendations/feedback body={} → 400 校验失败")
    void feedbackValidatesRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/feedback")
                        .with(jwt().jwt(j -> j.claim("aud", List.of("educloud-web")).subject("123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/recommendations/feedback 已登录（jwt subject=123 aud=educloud-web）→ 200")
    void feedbackWithAuthenticatedJwtSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/feedback")
                        .with(jwt().jwt(j -> j.claim("aud", List.of("educloud-web")).subject("123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":501,\"action\":\"DISLIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(feedbackService).dislike(eq(123L), eq(501L), isNull());
    }

    @Test
    @DisplayName("POST /api/v1/recommendations/feedback 匿名 → 401")
    void feedbackRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":501,\"action\":\"DISLIKE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);
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
