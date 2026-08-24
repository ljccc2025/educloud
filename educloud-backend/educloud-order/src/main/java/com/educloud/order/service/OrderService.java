package com.educloud.order.service;

import com.educloud.common.api.PageResponse;
import com.educloud.order.dto.request.OrderCreateRequest;
import com.educloud.order.dto.response.OrderDetailResponse;

public interface OrderService {

    OrderDetailResponse createOrder(Long studentId, OrderCreateRequest request, String headerIdempotencyToken);

    OrderDetailResponse getOrderDetail(Long studentId, Long orderId);

    PageResponse<OrderDetailResponse> listStudentOrders(Long studentId, String status, int page, int size);

    void cancelOrder(Long studentId, Long orderId);
}
