package com.educloud.user.messaging;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.educloud.common.messaging.EventEnvelope;
import com.educloud.user.entity.OutboxEventEntity;
import com.educloud.user.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 发布器：小批锁定 PENDING 事件，投递 RabbitMQ 后标记 PUBLISHED；失败退避重试，
 * 达阈值标记 FAILED 并告警（可靠性设计第 4.1 节；确认不明确允许重投，消费者幂等）。
 */
@Component
public final class OutboxEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxEventDispatcher.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventDispatcher(
            OutboxEventMapper outboxEventMapper,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${educloud.user.outbox.poll-interval:5000}")
    @Transactional
    public void dispatchPending() {
        Instant now = Instant.now();
        List<OutboxEventEntity> pending = outboxEventMapper.selectList(
                new QueryWrapper<OutboxEventEntity>()
                        .eq("publish_status", "PENDING")
                        .lt("attempt_count", MAX_ATTEMPTS)
                        .and(wrapper -> wrapper.isNull("next_attempt_at")
                                .or().le("next_attempt_at", now))
                        .orderByAsc("source_sequence")
                        .last("LIMIT " + BATCH_SIZE));
        for (OutboxEventEntity event : pending) {
            try {
                JsonNode data = objectMapper.readTree(event.getPayloadJson());
                EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                        event.getEventId(),
                        event.getEventType(),
                        event.getEventVersion(),
                        "educloud-user",
                        event.getSourceSequence(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getAggregateVersion(),
                        event.getOccurredAt(),
                        event.getRequestId(),
                        event.getTraceId(),
                        data);
                rabbitTemplate.convertAndSend(
                        event.getAggregateType() + ":" + event.getAggregateId(), envelope);
                outboxEventMapper.update(null, new UpdateWrapper<OutboxEventEntity>()
                        .eq("id", event.getId())
                        .set("publish_status", "PUBLISHED")
                        .set("published_at", now));
            } catch (Exception failure) {
                int attempts = event.getAttemptCount() + 1;
                boolean failed = attempts >= MAX_ATTEMPTS;
                outboxEventMapper.update(null, new UpdateWrapper<OutboxEventEntity>()
                        .eq("id", event.getId())
                        .set("attempt_count", attempts)
                        .set("next_attempt_at", now.plusSeconds(Math.min(300L, 5L * attempts)))
                        .set("publish_status", failed ? "FAILED" : "PENDING"));
                if (failed) {
                    LOGGER.error(
                            "Outbox event {} reached the retry limit and is marked FAILED",
                            event.getEventId());
                } else {
                    LOGGER.warn(
                            "Outbox event {} delivery failed, attempt {}",
                            event.getEventId(), attempts);
                }
            }
        }
    }
}
