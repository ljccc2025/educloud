package com.educloud.course.messaging;

import com.educloud.course.service.EnrollmentService;
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
public class PaymentRefundListener {

    private final EnrollmentService enrollmentService;

    @RabbitListener(queues = "${educloud.course.payment-refund-queue:course.payment.refund.queue}")
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Received invalid PaymentRefunded event: {}", event);
            return;
        }

        log.info("Received PaymentRefunded event: refundId={}, orderId={}, studentId={}",
                event.getRefundId(), event.getOrderId(), event.getUserId());

        try {
            enrollmentService.revokeCourseEnrollmentByOrder(event.getOrderId(), "PAYMENT_REFUNDED_" + event.getRefundId());
            log.info("Successfully revoked course enrollments for refunded order {}", event.getOrderId());
        } catch (Exception ex) {
            log.error("Failed to revoke enrollments for refunded order {}", event.getOrderId(), ex);
            throw ex;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRefundedEvent implements Serializable {
        private Long refundId;
        private Long paymentOrderId;
        private Long orderId;
        private Long userId;
        private Long refundAmountCents;
        private LocalDateTime refundedAt;
    }
}
