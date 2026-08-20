package com.educloud.common.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.educloud.common.error.CommonErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-20T08:00:00Z"), ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(responses))
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @Test
    void mapsBeanValidationToAStable400Response() throws Exception {
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
    void mapsUnreadableJsonWithoutLeakingParserDetails() throws Exception {
        String body = mockMvc.perform(post("/json")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("JsonEOFException", "HttpMessageNotReadableException");
    }

    @Test
    void mapsMethodParameterValidationToAStable400Response() throws Exception {
        mockMvc.perform(get("/method-validation")
                        .param("page", "0")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-method"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("req-method"))
                .andExpect(jsonPath("$.data.violations[0].field").value("page"));
    }

    @ParameterizedTest
    @MethodSource("businessStatuses")
    void mapsBusinessErrorsToHttpSemantics(CommonErrorCode errorCode, int expectedStatus)
            throws Exception {
        mockMvc.perform(get("/business/{errorCode}", errorCode.name())
                        .header(RequestContext.REQUEST_ID_HEADER, "req-business"))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-business"))
                .andExpect(jsonPath("$.code").value(errorCode.code()))
                .andExpect(jsonPath("$.requestId").value("req-business"));
    }

    @Test
    void mapsAnUnknownExceptionToOneSafe500AndLogsItOnce() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            String body = mockMvc.perform(get("/unexpected")
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

    private static Stream<Arguments> businessStatuses() {
        return Stream.of(
                Arguments.of(CommonErrorCode.VALIDATION_FAILED, 400),
                Arguments.of(CommonErrorCode.UNAUTHENTICATED, 401),
                Arguments.of(CommonErrorCode.ACCESS_DENIED, 403),
                Arguments.of(CommonErrorCode.VERSION_CONFLICT, 409),
                Arguments.of(CommonErrorCode.RATE_LIMITED, 429),
                Arguments.of(CommonErrorCode.DEPENDENCY_UNAVAILABLE, 503),
                Arguments.of(CommonErrorCode.INTERNAL_ERROR, 500));
    }

    @RestController
    private static final class FailureController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody Input input) {}

        @PostMapping("/json")
        void parse(@RequestBody Input input) {}

        @GetMapping("/business/{errorCode}")
        void business(@PathVariable CommonErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }

        @GetMapping("/method-validation")
        void methodValidation(@RequestParam @Positive int page) {}

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("jdbc:mysql://db.internal SQL secret");
        }
    }

    private record Input(@NotBlank String name) {}
}
