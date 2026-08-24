package com.educloud.order.messaging;

import com.educloud.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;

    public void onPaymentSucceeded(Long studentId, Long orderId) {
        log.info("Received PaymentSucceeded event for orderId={}, studentId={}", orderId, studentId);
        orderService.mockPay(studentId, orderId);
    }
}
