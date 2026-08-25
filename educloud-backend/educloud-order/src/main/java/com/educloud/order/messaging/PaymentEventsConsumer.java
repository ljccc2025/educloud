package com.educloud.order.messaging;

import com.educloud.order.service.OrderService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventsConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = "${educloud.order.payment-success-queue:order.payment.success.queue}")
    public void onPaymentSuccess(PaymentSucceededMessage message) {
        if (message == null || message.getOrderId() == null) {
            log.warn("Received invalid PaymentSucceededMessage: {}", message);
            return;
        }

        log.info("Received PaymentSucceededMessage: orderId={}, paymentOrderId={}, amountCents={}",
                message.getOrderId(), message.getPaymentOrderId(), message.getAmountCents());

        try {
            orderService.processPaymentSuccess(
                    message.getOrderId(),
                    message.getPaymentOrderId(),
                    message.getUserId(),
                    message.getAmountCents(),
                    message.getPaidAt());
        } catch (Exception ex) {
            log.error("Failed to process payment success for orderId={}", message.getOrderId(), ex);
            throw ex;
        }
    }

    @RabbitListener(queues = "${educloud.order.payment-refund-queue:order.payment.refund.queue}")
    public void onPaymentRefunded(PaymentRefundedMessage message) {
        if (message == null || message.getOrderId() == null) {
            log.warn("Received invalid PaymentRefundedMessage: {}", message);
            return;
        }

        log.info("Received PaymentRefundedMessage: orderId={}, refundId={}",
                message.getOrderId(), message.getRefundId());

        try {
            orderService.processPaymentRefund(
                    message.getOrderId(),
                    message.getRefundId(),
                    message.getRefundAmountCents(),
                    message.getRefundedAt());
        } catch (Exception ex) {
            log.error("Failed to process payment refund for orderId={}", message.getOrderId(), ex);
            throw ex;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSucceededMessage implements Serializable {
        private Long paymentOrderId;
        private Long orderId;
        private Long userId;
        private Long amountCents;
        private String channelCode;
        private String channelTradeNo;
        private LocalDateTime paidAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRefundedMessage implements Serializable {
        private Long refundId;
        private Long paymentOrderId;
        private Long orderId;
        private Long userId;
        private Long refundAmountCents;
        private LocalDateTime refundedAt;
    }
}
