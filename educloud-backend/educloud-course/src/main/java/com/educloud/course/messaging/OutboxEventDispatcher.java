package com.educloud.course.messaging;

import com.educloud.common.messaging.EventEnvelope;
import com.educloud.course.entity.OutboxEventEntity;
import com.educloud.course.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 事件投递器（BUG-042/058/064 修复）：定时扫描 outbox_event 中 PENDING 事件，
 * 反序列化 payload 组装 {@link EventEnvelope} 后经 RabbitTemplate 投递 MQ，成功 CAS 置
 * PUBLISHED；失败按模块原有退避（5s×尝试次数，300s 封顶）推迟重试；超过
 * {@link #MAX_ATTEMPTS} 次仍失败的事件 CAS 置 FAILED（终态，人工介入）。
 *
 * <p>多实例安全（CAS 认领）：每次调度先回置超过 {@link #STALE_CLAIM_SECONDS} 的
 * 陈旧 CLAIMED 认领（实例崩溃恢复），再循环批量 CAS 认领（单条 UPDATE 原子，
 * PENDING → CLAIMED 并写入本实例标识 claim_owner），随后仅取回本实例认领的
 * 事件逐条投递——认领/取回/终态均为独立短语句且天然原子，投递位于任何事务
 * 之外，多实例互不阻塞、同一事件仅被一个实例投递。</p>
 *
 * <p>M04 坑 3：routing key 为点分隔 {@code aggregateType.aggregateId}
 * （Course.10001 / Enrollment.70001），可被 Course.# / Enrollment.# 通配绑定命中。</p>
 */
@Component
public class OutboxEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxEventDispatcher.class);
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

    @Scheduled(fixedDelayString = "${educloud.course.outbox.poll-interval:5000}")
    public void dispatchPending() {
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
            JsonNode data = objectMapper.readTree(event.getPayloadJson());
            EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                    event.getEventId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    "educloud-course",
                    event.getSourceSequence(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getAggregateVersion(),
                    event.getOccurredAt(),
                    event.getRequestId(),
                    event.getTraceId(),
                    data);
            // Topic 交换机按点分隔段匹配：Course.123 可被 Course.# 绑定命中。
            rabbitTemplate.convertAndSend(
                    event.getAggregateType() + "." + event.getAggregateId(), envelope);
            outboxEventMapper.markPublished(event.getId());
        } catch (Exception failure) {
            int nextAttempt = event.getAttemptCount() + 1;
            Instant nextAttemptAt = Instant.now().plusSeconds(
                    Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS * nextAttempt));
            if (nextAttempt >= MAX_ATTEMPTS) {
                // 终态语义保持原有值：达阈值标记 FAILED，不再认领（人工介入）。
                outboxEventMapper.markFailed(event.getId(), nextAttemptAt);
                LOGGER.error("Outbox event {} reached the retry limit and is marked FAILED",
                        event.getEventId(), failure);
            } else {
                outboxEventMapper.markFailedAttempt(event.getId(), nextAttemptAt);
                LOGGER.warn("Outbox event {} delivery failed, attempt {}",
                        event.getEventId(), nextAttempt, failure);
            }
        }
    }
}
