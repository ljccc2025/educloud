package com.educloud.order.messaging;

import com.educloud.order.config.RabbitOrderConfig;
import com.educloud.order.messaging.dto.OrderDelayMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendDelayMessage(OrderDelayMessage message) {
        if (message == null || message.getOrderId() == null) {
            return;
        }
        try {
            log.info("Sending order delay cancel message: orderId={}, orderNo={}", message.getOrderId(), message.getOrderNo());
            rabbitTemplate.convertAndSend(
                    RabbitOrderConfig.ORDER_EXCHANGE,
                    RabbitOrderConfig.ORDER_DELAY_ROUTING_KEY,
                    message);
        } catch (Exception ex) {
            log.error("Failed to send order delay message to MQ, fallback without blocking order creation: orderId={}, orderNo={}",
                    message.getOrderId(), message.getOrderNo(), ex);
        }
    }
}
