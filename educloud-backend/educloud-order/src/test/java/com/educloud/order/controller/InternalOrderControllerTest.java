package com.educloud.order.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.order.dto.response.OrderFulfillmentSnapshotResponse;
import com.educloud.order.dto.response.OrderItemResponse;
import com.educloud.order.dto.response.OrderPayableSnapshotResponse;
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

class InternalOrderControllerTest {

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
        InternalOrderController controller = new InternalOrderController(orderService, responses);
        OrderExceptionHandler exceptionHandler = new OrderExceptionHandler(responses);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @Test
    void getPayableSnapshotReturnsSnapshot() throws Exception {
        OrderPayableSnapshotResponse snapshot = OrderPayableSnapshotResponse.builder()
                .orderId(1001L)
                .orderNo("ORD1001")
                .studentId(2001L)
                .status("PENDING_PAYMENT")
                .payableAmount(new BigDecimal("199.00"))
                .currency("CNY")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .items(List.of(OrderItemResponse.builder()
                        .id(5001L)
                        .orderId(1001L)
                        .courseId(9001L)
                        .unitPrice(new BigDecimal("199.00"))
                        .lineAmount(new BigDecimal("199.00"))
                        .build()))
                .build();

        when(orderService.getPayableSnapshot(1001L)).thenReturn(snapshot);

        mockMvc.perform(get("/internal/v1/orders/1001/payable-snapshot")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-internal-payable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderId").value("1001"))
                .andExpect(jsonPath("$.data.payableAmount").value(199.00))
                .andExpect(jsonPath("$.data.items[0].courseId").value("9001"));
    }

    @Test
    void getFulfillmentSnapshotReturnsSnapshot() throws Exception {
        OrderFulfillmentSnapshotResponse snapshot = OrderFulfillmentSnapshotResponse.builder()
                .orderId(1001L)
                .status("PAID")
                .aggregateVersion(1)
                .items(List.of(OrderItemResponse.builder()
                        .id(5001L)
                        .orderId(1001L)
                        .courseId(9001L)
                        .fulfillmentStatus("UNFULFILLED")
                        .build()))
                .build();

        when(orderService.getFulfillmentSnapshot(1001L)).thenReturn(snapshot);

        mockMvc.perform(get("/internal/v1/orders/1001/fulfillment-snapshot")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-internal-fulfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderId").value("1001"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.items[0].fulfillmentStatus").value("UNFULFILLED"));
    }

    @Test
    void getPayableSnapshotThrowsWhenNotFound() throws Exception {
        when(orderService.getPayableSnapshot(9999L))
                .thenThrow(new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/orders/9999/payable-snapshot")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-internal-nf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
