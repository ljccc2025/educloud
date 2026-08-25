package com.educloud.payment.messaging;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.payment.entity.PaymentOutboxEventEntity;
import com.educloud.payment.enums.OutboxStatus;
import com.educloud.payment.mapper.PaymentOutboxEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final PaymentOutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public void writeEvent(String aggregateType, Long aggregateId, String eventType, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload: " + eventType, e);
        }

        PaymentOutboxEventEntity entity = PaymentOutboxEventEntity.builder()
                .id(IdWorker.getId())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        outboxEventMapper.insert(entity);
        log.debug("Outbox event written: id={}, type={}, aggregateId={}", entity.getId(), eventType, aggregateId);
    }
}
