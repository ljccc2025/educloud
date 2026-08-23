package com.educloud.course.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.web.GlobalExceptionHandler;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * M05 任务 5（质量审查加固）：CourseErrorCode + CourseExceptionHandler 的 MockMvc
 * standalone 契约测试，复刻 educloud-file 的 FileExceptionHandlerTest。
 *
 * <p>断言：BusinessException 9 个 Course 错误码的 HTTP 状态/code/message/X-Request-Id 回显，
 * AccessDenied → 403 COURSE_ACCESS_DENIED，@Valid → 400 VALIDATION_FAILED + violations；
 * 双 advice 共存回归测试：坏 JSON 由 common 的 GlobalExceptionHandler 映射为 400（而非 500），
 * 未知异常 500 由 common 兜底且不泄漏内部细节、ERROR 日志仅 1 条，而 AccessDeniedException
 * 仍落到本 handler（验证 @Order：域 advice 先于 common 的 Exception 兜底）。</p>
 */
class CourseExceptionHandlerTest {

    private MockMvc mockMvc;
    private MockMvc dualAdviceMockMvc;

    @BeforeEach
    void setUp() {
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-23T08:00:00Z"), ZoneOffset.UTC));
        var courseAdvice = new CourseExceptionHandler(responses);
        var commonAdvice = new GlobalExceptionHandler(responses);
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(courseAdvice)
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
        // 与生产上下文一致：common 与域 advice 共存，靠 @Order 决定优先级（common 靠前）。
        dualAdviceMockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(commonAdvice, courseAdvice)
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @ParameterizedTest
    @MethodSource("businessErrorCodes")
    void mapsEveryCourseBusinessErrorCodeToStableErrorResponse(CourseErrorCode errorCode)
            throws Exception {
        String requestId = "req-" + errorCode.name();
        mockMvc.perform(get("/business/{code}", errorCode.name())
                        .header(RequestContext.REQUEST_ID_HEADER, requestId))
                .andExpect(status().is(errorCode.httpStatus()))
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, requestId))
                .andExpect(jsonPath("$.code").value(errorCode.code()))
                .andExpect(jsonPath("$.message").value(errorCode.defaultMessage()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mapsSpringSecurityAccessDeniedToCourseAccessDenied() throws Exception {
        mockMvc.perform(get("/denied")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.requestId").value("req-denied"));
    }

    @Test
    void mapsBeanValidationToStable400Response() throws Exception {
        mockMvc.perform(post("/validation")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-validation"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("req-validation"))
                .andExpect(jsonPath("$.data.violations[0].field").value("name"));
    }

    @Test
    void mapsAnUnknownExceptionToOneSafe500ViaCommonAndLogsItOnce() throws Exception {
        // 本 handler 无 Exception 兜底：未知异常由 common GlobalExceptionHandler 兜底为 500，
        // ERROR 日志记在 common 上（双 advice 共存场景，与生产一致）。
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            String body = dualAdviceMockMvc.perform(get("/unexpected")
                            .header(RequestContext.REQUEST_ID_HEADER, "req-500"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-500"))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal server error"))
                    .andExpect(jsonPath("$.requestId").value("req-500"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(body).doesNotContain(
                    "jdbc:mysql://db.internal",
                    "IllegalStateException",
                    "SQL",
                    "stackTrace");
            assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.ERROR))
                    .hasSize(1);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void dualAdviceCoexistencePrefersCommonForBadJsonAndCourseForAccessDenied()
            throws Exception {
        // 坏 JSON：common GlobalExceptionHandler 的 HttpMessageNotReadable → 400，
        // 不能被本 handler 的 Exception 兜底抢先映射为 500（防 @Order 顺序回归）。
        dualAdviceMockMvc.perform(post("/json")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("req-json"));

        // AccessDeniedException 无 common 专属映射，且本 handler 先于 common 的 Exception 兜底 → 403 COURSE_ACCESS_DENIED。
        dualAdviceMockMvc.perform(get("/denied")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-dual-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.requestId").value("req-dual-denied"));
    }

    private static Stream<CourseErrorCode> businessErrorCodes() {
        return Stream.of(CourseErrorCode.values());
    }

    @RestController
    private static final class FailureController {

        @GetMapping("/business/{code}")
        void business(@PathVariable CourseErrorCode code) {
            throw new BusinessException(code);
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("no");
        }

        @PostMapping("/validation")
        void validate(@Valid @RequestBody Input input) {}

        @PostMapping("/json")
        void json(@RequestBody Input input) {}

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("jdbc:mysql://db.internal SQL secret");
        }
    }

    private record Input(@NotBlank String name) {}
}