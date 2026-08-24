package com.educloud.course.messaging;

import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.service.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderPaidListenerTest {

    private EnrollmentService enrollmentService;
    private OrderPaidListener listener;

    @BeforeEach
    void setUp() {
        enrollmentService = mock(EnrollmentService.class);
        listener = new OrderPaidListener(enrollmentService);
    }

    @Test
    void enrollsStudentIntoCoursesUponReceivingOrderPaidEvent() {
        OrderPaidListener.OrderPaidEvent event = OrderPaidListener.OrderPaidEvent.builder()
                .orderId(1001L)
                .orderNo("ORD1001")
                .studentId(2001L)
                .courseIds(List.of(9001L, 9002L))
                .paidAmount(new BigDecimal("299.00"))
                .paidAt(LocalDateTime.now())
                .build();

        listener.onOrderPaid(event);

        verify(enrollmentService).enrollPaidCourse(eq(9001L), eq(2001L), eq(1001L));
        verify(enrollmentService).enrollPaidCourse(eq(9002L), eq(2001L), eq(1001L));
    }

    @Test
    void ignoresNullOrEmptyEventGracefully() {
        listener.onOrderPaid(null);
        verifyNoInteractions(enrollmentService);

        OrderPaidListener.OrderPaidEvent emptyEvent = new OrderPaidListener.OrderPaidEvent();
        listener.onOrderPaid(emptyEvent);
        verifyNoInteractions(enrollmentService);
    }
}
