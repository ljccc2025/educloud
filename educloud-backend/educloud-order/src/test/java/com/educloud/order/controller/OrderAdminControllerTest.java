package com.educloud.order.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.dto.response.OrderItemResponse;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.exception.OrderExceptionHandler;
import com.educloud.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderAdminControllerTest {

    private MockMvc mockMvc;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-24T08:00:00Z"), ZoneOffset.UTC));
        OrderAdminController controller = new OrderAdminController(orderService, responses);
        OrderExceptionHandler exceptionHandler = new OrderExceptionHandler(responses);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @Test
    void listOrdersReturnsPage() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .id(1001L)
                .orderNo("ORD1001")
                .studentId(2001L)
                .status("PAID")
                .payableAmount(new BigDecimal("199.00"))
                .items(List.of(OrderItemResponse.builder()
                        .id(5001L)
                        .orderId(1001L)
                        .courseId(9001L)
                        .courseTitleSnapshot("微服务实战")
                        .build()))
                .build();
        PageResponse<OrderDetailResponse> page = PageResponse.of(List.of(detail), 1, 10, 1);

        when(orderService.listAdminOrders("ORD1001", "PAID", 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("orderNo", "ORD1001")
                        .param("status", "PAID")
                        .param("page", "1")
                        .param("size", "10")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-admin-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("1001"))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("ORD1001"));
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

        when(orderService.getAdminOrderDetail(1001L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/orders/1001")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-admin-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD1001"));
    }

    @Test
    void getOrderDetailThrowsWhenNotFound() throws Exception {
        when(orderService.getAdminOrderDetail(9999L))
                .thenThrow(new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/admin/orders/9999")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-admin-nf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
