package com.educloud.payment.messaging;

import com.educloud.payment.config.RabbitPaymentConfig;
import com.educloud.payment.entity.PaymentOutboxEventEntity;
import com.educloud.payment.enums.OutboxStatus;
import com.educloud.payment.mapper.PaymentOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxRelay {

    private static final int MAX_RETRY_COUNT = 5;

    private final PaymentOutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    public void processOutboxEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentOutboxEventEntity> pendingEvents = outboxEventMapper.findPendingEvents(now, 50);
        if (pendingEvents == null || pendingEvents.isEmpty()) {
            return;
        }

        for (PaymentOutboxEventEntity event : pendingEvents) {
            // CAS 抢占
            int updated = outboxEventMapper.updateStatusCas(event.getId(), OutboxStatus.PENDING, OutboxStatus.SENDING);
            if (updated == 0) {
                continue;
            }

            try {
                String routingKey = getRoutingKey(event.getEventType());
                MessageProperties messageProperties = new MessageProperties();
                messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                messageProperties.setHeader("eventType", event.getEventType());
                messageProperties.setHeader("aggregateId", event.getAggregateId());

                Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), messageProperties);
                rabbitTemplate.send(RabbitPaymentConfig.PAYMENT_EXCHANGE, routingKey, message);

                outboxEventMapper.markPublished(event.getId(), LocalDateTime.now());
                log.info("Successfully published outbox event: id={}, type={}, routingKey={}",
                        event.getId(), event.getEventType(), routingKey);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, type={}. Error: {}",
                        event.getId(), event.getEventType(), e.getMessage(), e);

                int nextRetryCount = event.getRetryCount() + 1;
                OutboxStatus nextStatus = nextRetryCount >= MAX_RETRY_COUNT ? OutboxStatus.FAILED : OutboxStatus.PENDING;
                long backoffSeconds = 1L << Math.min(nextRetryCount, 5); // 1s, 2s, 4s, 8s, 16s, 32s
                LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(backoffSeconds);

                outboxEventMapper.markFailed(event.getId(), nextStatus, nextRetryTime);
            }
        }
    }

    private String getRoutingKey(String eventType) {
        if ("PaymentSucceededEvent".equalsIgnoreCase(eventType) || "PAYMENT_SUCCEEDED".equalsIgnoreCase(eventType)) {
            return RabbitPaymentConfig.ROUTING_KEY_PAYMENT_SUCCEEDED;
        }
        if ("PaymentRefundedEvent".equalsIgnoreCase(eventType) || "PAYMENT_REFUNDED".equalsIgnoreCase(eventType)) {
            return RabbitPaymentConfig.ROUTING_KEY_PAYMENT_REFUNDED;
        }
        return "payment.event";
    }
}
