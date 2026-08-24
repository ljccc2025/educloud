package com.educloud.order.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.order.dto.response.OrderFulfillmentSnapshotResponse;
import com.educloud.order.dto.response.OrderPayableSnapshotResponse;
import com.educloud.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;
    private final ApiResponseFactory responses;

    @GetMapping("/{id}/payable-snapshot")
    public ApiResponse<OrderPayableSnapshotResponse> getPayableSnapshot(@PathVariable Long id) {
        return responses.success(orderService.getPayableSnapshot(id));
    }

    @GetMapping("/{id}/fulfillment-snapshot")
    public ApiResponse<OrderFulfillmentSnapshotResponse> getFulfillmentSnapshot(@PathVariable Long id) {
        return responses.success(orderService.getFulfillmentSnapshot(id));
    }
}
