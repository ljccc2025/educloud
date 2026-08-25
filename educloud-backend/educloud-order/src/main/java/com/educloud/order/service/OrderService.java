package com.educloud.order.service;

import com.educloud.common.api.PageResponse;
import com.educloud.order.dto.request.OrderCreateRequest;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.dto.response.OrderFulfillmentSnapshotResponse;
import com.educloud.order.dto.response.OrderPayableSnapshotResponse;

public interface OrderService {

    OrderDetailResponse createOrder(Long studentId, OrderCreateRequest request, String headerIdempotencyToken);

    OrderDetailResponse getOrderDetail(Long studentId, Long orderId);

    PageResponse<OrderDetailResponse> listStudentOrders(Long studentId, String status, int page, int size);

    void cancelOrder(Long studentId, Long orderId);

    OrderDetailResponse mockPay(Long studentId, Long orderId);

    OrderPayableSnapshotResponse getPayableSnapshot(Long orderId);

    OrderFulfillmentSnapshotResponse getFulfillmentSnapshot(Long orderId);

    PageResponse<OrderDetailResponse> listAdminOrders(String orderNo, String status, int page, int size);

    OrderDetailResponse getAdminOrderDetail(Long orderId);

    void processPaymentSuccess(Long orderId, Long paymentOrderId, Long userId, Long amountCents, java.time.LocalDateTime paidAt);

    void processPaymentRefund(Long orderId, Long refundId, Long refundAmountCents, java.time.LocalDateTime refundedAt);
}
