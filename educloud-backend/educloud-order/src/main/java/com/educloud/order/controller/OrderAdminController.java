package com.educloud.order.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;
    private final ApiResponseFactory responses;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('order:admin', 'ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_FINANCE_ADMIN')")
    public ApiResponse<PageResponse<OrderDetailResponse>> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return responses.success(orderService.listAdminOrders(orderNo, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('order:admin', 'ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_FINANCE_ADMIN')")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable Long id) {
        return responses.success(orderService.getAdminOrderDetail(id));
    }
}
