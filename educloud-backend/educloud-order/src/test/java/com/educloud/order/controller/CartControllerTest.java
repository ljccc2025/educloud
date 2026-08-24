package com.educloud.order.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.order.dto.request.CartAddRequest;
import com.educloud.order.dto.request.CartSelectionRequest;
import com.educloud.order.dto.response.CartItemResponse;
import com.educloud.order.dto.response.CartSummaryResponse;
import com.educloud.order.exception.OrderExceptionHandler;
import com.educloud.order.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerTest {

    private MockMvc mockMvc;
    private CartService cartService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        objectMapper = new ObjectMapper();
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-24T08:00:00Z"), ZoneOffset.UTC));
        CartController controller = new CartController(cartService, responses);
        OrderExceptionHandler exceptionHandler = new OrderExceptionHandler(responses);

        Jwt mockJwt = new Jwt(
                "token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "2001", "permissions", List.of("order:view", "order:create")));

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
    void getCartReturnsSummary() throws Exception {
        CartSummaryResponse summary = CartSummaryResponse.builder()
                .items(List.of(CartItemResponse.builder()
                        .id(1001L)
                        .courseId(9001L)
                        .courseTitle("微服务架构实战")
                        .unitPrice(new BigDecimal("199.00"))
                        .selected(true)
                        .isOnSale(true)
                        .createdAt(LocalDateTime.now())
                        .build()))
                .totalCount(1)
                .selectedCount(1)
                .selectedAmount(new BigDecimal("199.00"))
                .build();
        when(cartService.getCartSummary(2001L)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/cart")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-cart-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value("1001"))
                .andExpect(jsonPath("$.data.items[0].courseId").value("9001"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.selectedCount").value(1))
                .andExpect(jsonPath("$.data.selectedAmount").value(199.00));
    }

    @Test
    void addItemAddsToCart() throws Exception {
        CartItemResponse itemResponse = CartItemResponse.builder()
                .id(1001L)
                .courseId(9001L)
                .selected(true)
                .build();
        when(cartService.addItem(eq(2001L), any(CartAddRequest.class))).thenReturn(itemResponse);

        CartAddRequest request = CartAddRequest.builder().courseId(9001L).build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-cart-add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.courseId").value("9001"));
    }

    @Test
    void updateSelectionUpdatesItem() throws Exception {
        CartSelectionRequest request = CartSelectionRequest.builder().selected(false).build();

        mockMvc.perform(put("/api/v1/cart/items/9001/selection")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-cart-sel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(cartService).updateSelection(2001L, 9001L, false);
    }

    @Test
    void removeItemRemovesItem() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items/9001")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-cart-del"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(cartService).removeItem(2001L, 9001L);
    }

    @Test
    void clearCartClearsItems() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items")
                        .param("onlySelected", "true")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-cart-clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(cartService).clearCart(2001L, true);
    }
}
