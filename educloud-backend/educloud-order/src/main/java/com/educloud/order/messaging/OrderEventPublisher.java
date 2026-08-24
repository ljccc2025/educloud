package com.educloud.order.messaging;

import com.educloud.order.config.RabbitOrderConfig;
import com.educloud.order.messaging.dto.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderPaid(OrderPaidEvent event) {
        if (event == null || event.getOrderId() == null) {
            return;
        }
        try {
            log.info("Publishing OrderPaid event: orderId={}, orderNo={}, studentId={}, courseIds={}",
                    event.getOrderId(), event.getOrderNo(), event.getStudentId(), event.getCourseIds());
            rabbitTemplate.convertAndSend(
                    RabbitOrderConfig.ORDER_EVENT_EXCHANGE,
                    RabbitOrderConfig.ORDER_PAID_ROUTING_KEY,
                    event);
        } catch (Exception ex) {
            log.error("Failed to publish OrderPaid event: orderId={}, orderNo={}", event.getOrderId(), event.getOrderNo(), ex);
        }
    }
}
