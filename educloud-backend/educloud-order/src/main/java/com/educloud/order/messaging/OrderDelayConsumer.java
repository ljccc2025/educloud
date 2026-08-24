package com.educloud.order.messaging;

import com.educloud.order.config.RabbitOrderConfig;
import com.educloud.order.entity.OrderStatus;
import com.educloud.order.entity.TradeOrderEntity;
import com.educloud.order.mapper.TradeOrderMapper;
import com.educloud.order.messaging.dto.OrderDelayMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayConsumer {

    private final TradeOrderMapper tradeOrderMapper;

    @RabbitListener(queues = RabbitOrderConfig.ORDER_CANCEL_QUEUE)
    public void onOrderTimeout(OrderDelayMessage message) {
        if (message == null || message.getOrderId() == null) {
            log.warn("Received empty order timeout message: {}", message);
            return;
        }

        Long orderId = message.getOrderId();
        log.info("Received order timeout cancel event: orderId={}, orderNo={}", orderId, message.getOrderNo());

        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("Order not found for timeout cancel: orderId={}", orderId);
            return;
        }

        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            log.info("Order status is {}, skipping timeout cancel: orderId={}", order.getStatus(), orderId);
            return;
        }

        int rows = tradeOrderMapper.updateStatusToCancelledWithCas(
                orderId, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.CANCELLED.name(), LocalDateTime.now());
        if (rows > 0) {
            log.info("Order timeout cancelled successfully: orderId={}", orderId);
        } else {
            log.warn("Order timeout cancel CAS update failed (status may have changed concurrently): orderId={}", orderId);
        }
    }
}
