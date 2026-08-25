package com.educloud.order.messaging;

import com.educloud.order.config.RabbitOrderConfig;
import com.educloud.order.messaging.dto.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单领域事件发布器（BUG-017 修复）：发送失败异常直接上抛（由
 * {@link OutboxRelay} 捕获并按退避策略重试），不再吞掉——原实现仅记
 * 日志导致已付款订单的履约事件永久丢失、用户付款后永不开课。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderPaid(OrderPaidEvent event) {
        if (event == null || event.getOrderId() == null) {
            return;
        }
        log.info("Publishing OrderPaid event: orderId={}, orderNo={}, studentId={}, courseIds={}",
                event.getOrderId(), event.getOrderNo(), event.getStudentId(), event.getCourseIds());
        rabbitTemplate.convertAndSend(
                RabbitOrderConfig.ORDER_EVENT_EXCHANGE,
                RabbitOrderConfig.ORDER_PAID_ROUTING_KEY,
                event);
    }
}
