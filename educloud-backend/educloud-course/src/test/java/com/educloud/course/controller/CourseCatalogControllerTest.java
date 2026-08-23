package com.educloud.course.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.dto.request.CourseListQuery;
import com.educloud.course.dto.response.CourseDetailResponse;
import com.educloud.course.dto.response.CourseSummaryResponse;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.service.CourseCatalogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 11：GET /api/v1/courses 与 GET /api/v1/courses/{id} 控制器测试。
 *
 * <p>依据：规格 §6 —— 列表/详情匿名可达（SecurityConfig 放行 GET，网关 PUBLIC_READ
 * 已放行）；已登录请求透传 JWT subject 作为当前 userId（enrolled/教师视角）；非法
 * sort 400；他人/匿名读非发布课程 404 由服务层抛 COURSE_NOT_FOUND（控制器透传）。
 * @WebMvcTest + 真实 SecurityConfig/ApiResponseFactory（仿 CategoryControllerTest），
 * 仅 mock CourseCatalogService 与 JwtDecoder。</p>
 */
@WebMvcTest(controllers = CourseCatalogController.class)
@Import({SecurityConfig.class, CourseCatalogControllerTest.TestInfrastructure.class})
class CourseCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseCatalogService catalogService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseProperties courseProperties;

    @Test
    void listIsReachableWithoutAuthenticationAndForwardsQuery() throws Exception {
        PageResponse<CourseSummaryResponse> page = PageResponse.of(List.of(
                new CourseSummaryResponse("101", "高等数学", null, "1001", "数学",
                        "BEGINNER", "199.00", new BigDecimal("4.50"), 12, 345, false)),
                1, 20, 1);
        when(catalogService.list(any(CourseListQuery.class), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/v1/courses")
                        .param("keyword", "数学")
                        .param("categoryId", "5")
                        .param("level", "BEGINNER")
                        .param("priceRange", "under200")
                        .param("sort", "price-asc")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value("101"))
                .andExpect(jsonPath("$.data.items[0].title").value("高等数学"))
                .andExpect(jsonPath("$.data.items[0].teacherName").value("1001"))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("数学"))
                .andExpect(jsonPath("$.data.items[0].level").value("BEGINNER"))
                .andExpect(jsonPath("$.data.items[0].price").value("199.00"))
                .andExpect(jsonPath("$.data.items[0].ratingAvg").value(4.5))
                .andExpect(jsonPath("$.data.items[0].ratingCount").value(12))
                .andExpect(jsonPath("$.data.items[0].enrollmentCount").value(345))
                .andExpect(jsonPath("$.data.items[0].enrolled").value(false))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1));

        ArgumentCaptor<CourseListQuery> captor = ArgumentCaptor.forClass(CourseListQuery.class);
        verify(catalogService).list(captor.capture(), isNull());
        CourseListQuery forwarded = captor.getValue();
        assertThat(forwarded.keyword()).isEqualTo("数学");
        assertThat(forwarded.categoryId()).isEqualTo("5");
        assertThat(forwarded.level()).isEqualTo("BEGINNER");
        assertThat(forwarded.priceRange()).isEqualTo("under200");
        assertThat(forwarded.sort()).isEqualTo("price-asc");
        assertThat(forwarded.page()).isEqualTo(1);
        assertThat(forwarded.size()).isEqualTo(20);
    }

    @Test
    void detailIsReachableWithoutAuthentication() throws Exception {
        when(catalogService.detail(eq(101L), isNull())).thenReturn(detailDto("PUBLISHED"));

        mockMvc.perform(get("/api/v1/courses/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("101"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PUBLISHED"));

        verify(catalogService).detail(eq(101L), isNull());
    }

    @Test
    void teacherSeesOwnDraftDetailWithUserIdFromJwt() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("1001", List.of("course:update")));
        when(catalogService.detail(eq(101L), eq(1001L))).thenReturn(detailDto("DRAFT"));

        mockMvc.perform(get("/api/v1/courses/101")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DRAFT"));

        verify(catalogService).detail(eq(101L), eq(1001L));
    }

    @Test
    void otherTeacherGets404ForNonPublishedCourse() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(token("2002", List.of()));
        when(catalogService.detail(eq(101L), eq(2002L)))
                .thenThrow(new BusinessException(CourseErrorCode.COURSE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/courses/101")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void invalidSortReturns400() throws Exception {
        when(catalogService.list(any(CourseListQuery.class), isNull()))
                .thenThrow(new BusinessException(
                        CommonErrorCode.VALIDATION_FAILED, "sort is invalid"));

        mockMvc.perform(get("/api/v1/courses").param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private static CourseDetailResponse detailDto(String lifecycleStatus) {
        return new CourseDetailResponse(
                "101", "高等数学", "副标题", "描述", null, "BEGINNER", "199.00", "CNY",
                "5", "数学",
                List.of(new CourseDetailResponse.Teacher("1001", "OWNER")),
                new BigDecimal("4.50"), 12, 345, false,
                lifecycleStatus, List.of());
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
