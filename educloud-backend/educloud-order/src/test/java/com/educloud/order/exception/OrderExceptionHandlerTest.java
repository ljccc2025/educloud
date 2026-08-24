package com.educloud.order.exception;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.web.GlobalExceptionHandler;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderExceptionHandlerTest {

    private MockMvc mockMvc;
    private MockMvc dualAdviceMockMvc;

    @BeforeEach
    void setUp() {
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-24T08:00:00Z"), ZoneOffset.UTC));
        var orderAdvice = new OrderExceptionHandler(responses);
        var commonAdvice = new GlobalExceptionHandler(responses);
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(orderAdvice)
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
        dualAdviceMockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(commonAdvice, orderAdvice)
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @ParameterizedTest
    @MethodSource("businessErrorCodes")
    void mapsEveryOrderBusinessErrorCodeToStableErrorResponse(OrderErrorCode errorCode) throws Exception {
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
    void mapsSpringSecurityAccessDeniedToOrderAccessDenied() throws Exception {
        mockMvc.perform(get("/denied")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"))
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
    void mapsAnUnknownExceptionToOneSafe500ViaCommon() throws Exception {
        dualAdviceMockMvc.perform(get("/unexpected")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-500"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.requestId").value("req-500"));
    }

    @Test
    void dualAdviceCoexistencePrefersCommonForBadJsonAndOrderForAccessDenied() throws Exception {
        dualAdviceMockMvc.perform(post("/json")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("req-json"));

        dualAdviceMockMvc.perform(get("/denied")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-dual-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"))
                .andExpect(jsonPath("$.requestId").value("req-dual-denied"));
    }

    private static Stream<OrderErrorCode> businessErrorCodes() {
        return Stream.of(OrderErrorCode.values());
    }

    @RestController
    private static final class FailureController {

        @GetMapping("/business/{code}")
        void business(@PathVariable OrderErrorCode code) {
            throw new OrderBizException(code);
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
