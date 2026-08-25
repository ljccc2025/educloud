package com.educloud.order.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.order.config.OrderProperties;
import com.educloud.order.dto.request.OrderCreateRequest;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.security.JwtSecurityUtils;
import com.educloud.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderStudentController {

    private final OrderService orderService;
    private final OrderProperties orderProperties;
    private final ApiResponseFactory responses;

    @PostMapping
    public ApiResponse<OrderDetailResponse> createOrder(
            @RequestBody(required = false) OrderCreateRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        if (request == null) {
            request = new OrderCreateRequest();
        }
        return responses.success(orderService.createOrder(studentId, request, idempotencyKeyHeader));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderDetailResponse>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(orderService.listStudentOrders(studentId, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(orderService.getOrderDetail(studentId, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        orderService.cancelOrder(studentId, id);
        return responses.success(null);
    }

    @PostMapping("/{id}/mock-pay")
    public ApiResponse<OrderDetailResponse> mockPay(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        // BUG-022 修复：mock-pay 仅限 local/dev 环境（fail-closed：环境缺失
        // 或为 test/staging/prod 一律 403），防止非本地部署后免费刷单资损。
        String environment = orderProperties.environment();
        if (!"local".equalsIgnoreCase(environment) && !"dev".equalsIgnoreCase(environment)) {
            throw new OrderBizException(OrderErrorCode.ORDER_ACCESS_DENIED,
                    "mock-pay 仅在本地/开发环境可用");
        }
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(orderService.mockPay(studentId, id));
    }
}
