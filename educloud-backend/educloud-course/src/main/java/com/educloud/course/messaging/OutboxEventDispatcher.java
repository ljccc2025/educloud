package com.educloud.course.messaging;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 发布器（M05 任务 15，复用 file/user 模式）：小批领取 PENDING 事件，组装
 * {@link EventEnvelope} 投递 RabbitMQ Topic 交换机 educloud.events 后标记 PUBLISHED；
 * 失败退避重试（attempt_count+1、next_attempt_at 排程），达阈值标记 FAILED 并告警
 * （可靠性设计第 4.1 节；确认不明确允许重投，消费者幂等）。
 *
 * <p>M04 坑 3：routing key 为点分隔 {@code aggregateType.aggregateId}
 * （Course.10001 / Enrollment.70001），可被 Course.# / Enrollment.# 通配绑定命中。
 * 并发安全：成功与失败的状态迁移均带 {@code publish_status='PENDING'} 条件更新
 * （WHERE id=? AND publish_status='PENDING'），防止已发布/已失败行被并发覆盖。
 * 注意：当前轮询不带 FOR UPDATE SKIP LOCKED，条件更新不阻止多实例重复投递；
 * at-least-once 语义已在可靠性设计 4.1.4 声明（消费者幂等），多实例去重
 * （FOR UPDATE SKIP LOCKED / 租约）在 M06+ 引入消费者后按需评估。</p>
 */
@Component
public class OutboxEventDispatcher {

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

    @Scheduled(fixedDelayString = "${educloud.course.outbox.poll-interval:5000}")
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
                outboxEventMapper.update(null, new UpdateWrapper<OutboxEventEntity>()
                        .eq("id", event.getId())
                        .eq("publish_status", "PENDING")
                        .set("publish_status", "PUBLISHED")
                        .set("published_at", now));
            } catch (Exception failure) {
                int attempts = event.getAttemptCount() + 1;
                boolean failed = attempts >= MAX_ATTEMPTS;
                outboxEventMapper.update(null, new UpdateWrapper<OutboxEventEntity>()
                        .eq("id", event.getId())
                        .eq("publish_status", "PENDING")
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
