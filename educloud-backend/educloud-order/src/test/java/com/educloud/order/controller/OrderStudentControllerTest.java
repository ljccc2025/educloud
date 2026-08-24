package com.educloud.order.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.order.dto.request.OrderCreateRequest;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.dto.response.OrderItemResponse;
import com.educloud.order.exception.OrderExceptionHandler;
import com.educloud.order.service.OrderService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderStudentControllerTest {

    private MockMvc mockMvc;
    private OrderService orderService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        objectMapper = new ObjectMapper();
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-24T08:00:00Z"), ZoneOffset.UTC));
        OrderStudentController controller = new OrderStudentController(orderService, responses);
        OrderExceptionHandler exceptionHandler = new OrderExceptionHandler(responses);

        Jwt mockJwt = new Jwt(
                "token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "2001", "permissions", List.of("order:create", "order:view")));

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
    void createOrderReturnsOrderDetail() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(1001L)
                .orderNo("ORD1001")
                .studentId(2001L)
                .status("PENDING_PAYMENT")
                .originalAmount(new BigDecimal("199.00"))
                .payableAmount(new BigDecimal("199.00"))
                .currency("CNY")
                .countdownSeconds(900L)
                .items(List.of(OrderItemResponse.builder()
                        .id(5001L)
                        .orderId(1001L)
                        .courseId(9001L)
                        .courseTitleSnapshot("微服务实战")
                        .unitPrice(new BigDecimal("199.00"))
                        .quantity(1)
                        .lineAmount(new BigDecimal("199.00"))
                        .fulfillmentStatus("UNFULFILLED")
                        .build()))
                .build();

        when(orderService.createOrder(eq(2001L), any(OrderCreateRequest.class), eq("tok-header")))
                .thenReturn(detail);

        OrderCreateRequest request = OrderCreateRequest.builder().courseId(9001L).build();

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Idempotency-Key", "tok-header")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-create-ord")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD1001"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.items[0].courseId").value("9001"));
    }

    @Test
    void getOrderDetailReturnsDetail() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(1001L)
                .orderNo("ORD1001")
                .studentId(2001L)
                .status("PENDING_PAYMENT")
                .payableAmount(new BigDecimal("199.00"))
                .build();
        when(orderService.getOrderDetail(2001L, 1001L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/1001")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-get-ord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD1001"));
    }

    @Test
    void listOrdersReturnsPage() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(1001L)
                .orderNo("ORD1001")
                .studentId(2001L)
                .status("PENDING_PAYMENT")
                .payableAmount(new BigDecimal("199.00"))
                .build();
        PageResponse<OrderDetailResponse> page = PageResponse.of(List.of(detail), 1, 10, 1);
        when(orderService.listStudentOrders(2001L, "PENDING_PAYMENT", 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "PENDING_PAYMENT")
                        .param("page", "1")
                        .param("size", "10")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-list-ord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("1001"));
    }

    @Test
    void cancelOrderCancels() throws Exception {
        mockMvc.perform(post("/api/v1/orders/1001/cancel")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-cancel-ord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(orderService).cancelOrder(2001L, 1001L);
    }

    @Test
    void mockPayReturnsOrderDetail() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(1001L)
                .orderNo("ORD1001")
                .studentId(2001L)
                .status("PAID")
                .payableAmount(new BigDecimal("199.00"))
                .build();
        when(orderService.mockPay(2001L, 1001L)).thenReturn(detail);

        mockMvc.perform(post("/api/v1/orders/1001/mock-pay")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-mock-pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }
}
