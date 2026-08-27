package com.educloud.order.messaging;

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
import java.util.UUID;

/**
 * Outbox 事件投递器（BUG-042/058/064 修复）：定时扫描 outbox_event 中 PENDING 事件，
 * 反序列化 payload 后经 {@link OrderEventPublisher} 投递 MQ，成功 CAS 置
 * PUBLISHED，失败按指数退避（5s→300s 封顶）推迟重试；超过
 * {@link #MAX_ATTEMPTS} 次仍失败的事件不再扫描（终态失败，人工介入）。
 *
 * <p>多实例安全（CAS 认领）：每次调度先回置超过 {@link #STALE_CLAIM_SECONDS} 的
 * 陈旧 CLAIMED 认领（实例崩溃恢复），再批量 CAS 认领（单条 UPDATE 原子，
 * PENDING → CLAIMED 并写入本实例标识 claim_owner），随后仅取回本实例认领的
 * 事件逐条投递——认领/取回/终态均为独立短语句且天然原子，投递位于任何事务
 * 之外，多实例互不阻塞、同一事件仅被一个实例投递。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    /** 单次调度最多认领批数，防止持续有新事件时无限循环。 */
    private static final int MAX_BATCHES = 10;
    /** 认领超时秒数：超过视为实例崩溃，回置 PENDING 供其他实例重新认领。 */
    private static final int STALE_CLAIM_SECONDS = 300;
    private static final long BASE_DELAY_SECONDS = 5L;
    private static final long MAX_DELAY_SECONDS = 300L;

    /** 本实例认领标识（JVM 生命周期唯一；崩溃后其认领由 STALE_CLAIM_SECONDS 超时回置）。 */
    private final String claimOwner = UUID.randomUUID().toString();

    private final OutboxEventMapper outboxEventMapper;
    private final OrderEventPublisher orderEventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void relayPendingEvents() {
        // 1) 回置实例崩溃遗留的陈旧认领（5 分钟超时）。
        outboxEventMapper.releaseStaleClaims(STALE_CLAIM_SECONDS);

        // 2) 循环 CAS 认领批次并逐条投递，直到认领不到或达到批数上限。
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int claimed = outboxEventMapper.claimPending(claimOwner, MAX_ATTEMPTS, BATCH_SIZE);
            if (claimed == 0) {
                return;
            }
            List<OutboxEventEntity> events = outboxEventMapper.selectClaimedByOwner(claimOwner);
            if (events == null || events.isEmpty()) {
                return;
            }
            for (OutboxEventEntity event : events) {
                dispatch(event);
            }
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
