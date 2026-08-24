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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidListener {

    private final EnrollmentService enrollmentService;

    @RabbitListener(queues = "${educloud.course.order-paid-queue:educloud.course.order-paid.queue}")
    public void onOrderPaid(OrderPaidEvent event) {
        if (event == null || event.getStudentId() == null || event.getCourseIds() == null) {
            log.warn("Received invalid OrderPaid event: {}", event);
            return;
        }

        log.info("Received OrderPaid event: orderId={}, studentId={}, courses={}",
                event.getOrderId(), event.getStudentId(), event.getCourseIds());

        for (Long courseId : event.getCourseIds()) {
            if (courseId == null) {
                continue;
            }
            try {
                enrollmentService.enrollPaidCourse(courseId, event.getStudentId(), event.getOrderId());
                log.info("Enrolled student {} into course {} from order {}",
                        event.getStudentId(), courseId, event.getOrderId());
            } catch (Exception ex) {
                log.error("Failed to enroll student {} into course {} from order {}",
                        event.getStudentId(), courseId, event.getOrderId(), ex);
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPaidEvent implements Serializable {
        private Long orderId;
        private String orderNo;
        private Long studentId;
        private List<Long> courseIds;
        private BigDecimal paidAmount;
        private LocalDateTime paidAt;
    }
}
