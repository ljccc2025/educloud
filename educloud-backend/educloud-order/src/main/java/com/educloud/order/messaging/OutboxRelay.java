package com.educloud.order.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.order.entity.OutboxEventEntity;
import com.educloud.order.mapper.OutboxEventMapper;
import com.educloud.order.messaging.dto.OrderPaidEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 事件投递器（BUG-017 修复）：定时扫描 outbox_event 中 PENDING 事件，
 * 反序列化 payload 后经 {@link OrderEventPublisher} 投递 MQ，成功 CAS 置
 * PUBLISHED，失败按指数退避（5s→300s 封顶）推迟重试；超过
 * {@link #MAX_ATTEMPTS} 次仍失败的事件不再扫描（终态失败，人工介入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    private static final long BASE_DELAY_SECONDS = 5L;
    private static final long MAX_DELAY_SECONDS = 300L;

    private final OutboxEventMapper outboxEventMapper;
    private final OrderEventPublisher orderEventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void relayPendingEvents() {
        List<OutboxEventEntity> pending = outboxEventMapper.selectList(
                new LambdaQueryWrapper<OutboxEventEntity>()
                        .eq(OutboxEventEntity::getPublishStatus, "PENDING")
                        .lt(OutboxEventEntity::getAttemptCount, MAX_ATTEMPTS)
                        .and(wrapper -> wrapper.isNull(OutboxEventEntity::getNextAttemptAt)
                                .or().le(OutboxEventEntity::getNextAttemptAt, LocalDateTime.now()))
                        .orderByAsc(OutboxEventEntity::getSourceSequence)
                        .last("LIMIT " + BATCH_SIZE));
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (OutboxEventEntity event : pending) {
            dispatch(event);
        }
    }

    private void dispatch(OutboxEventEntity event) {
        try {
            OrderPaidEvent payload = objectMapper.readValue(event.getPayloadJson(), OrderPaidEvent.class);
            orderEventPublisher.publishOrderPaid(payload);
            outboxEventMapper.markPublished(event.getId());
        } catch (Exception failure) {
            int nextAttempt = event.getAttemptCount() + 1;
            long delaySeconds = Math.min(MAX_DELAY_SECONDS,
                    BASE_DELAY_SECONDS * (1L << Math.min(event.getAttemptCount(), 6)));
            outboxEventMapper.markFailedAttempt(event.getId(), LocalDateTime.now().plusSeconds(delaySeconds));
            if (nextAttempt >= MAX_ATTEMPTS) {
                log.error("Outbox event relay failed after {} attempts, giving up (manual intervention required): "
                        + "eventId={}, orderId={}, eventType={}",
                        nextAttempt, event.getEventId(), event.getAggregateId(), event.getEventType(), failure);
            } else {
                log.warn("Outbox event relay failed, retry in {}s: eventId={}, orderId={}, attempt={}",
                        delaySeconds, event.getEventId(), event.getAggregateId(), nextAttempt, failure);
            }
        }
    }
}
