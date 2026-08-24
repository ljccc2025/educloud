package com.educloud.order.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.order.exception.OrderExceptionHandler;
import com.educloud.order.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyControllerTest {

    private MockMvc mockMvc;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = mock(IdempotencyService.class);
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-24T08:00:00Z"), ZoneOffset.UTC));
        IdempotencyController controller = new IdempotencyController(idempotencyService, responses);
        OrderExceptionHandler exceptionHandler = new OrderExceptionHandler(responses);

        Jwt mockJwt = new Jwt(
                "token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "2001", "permissions", List.of("order:create")));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory) {
                        return mockJwt;
                    }
                })
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @Test
    void getIdempotencyTokenReturnsToken() throws Exception {
        when(idempotencyService.generateToken(2001L)).thenReturn("token-abc-123");

        mockMvc.perform(get("/api/v1/orders/idempotency-token")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("token-abc-123"));
    }
}
