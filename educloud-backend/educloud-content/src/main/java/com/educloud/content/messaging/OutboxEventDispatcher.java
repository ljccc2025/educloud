package com.educloud.content.messaging;

import com.educloud.common.messaging.EventEnvelope;
import com.educloud.content.entity.OutboxEventEntity;
import com.educloud.content.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox 事件投递器（角色化动态流阶段 2）：content 模块此前仅写 outbox_event 无投递器，
 * 事件无法到达 RabbitMQ；参照 course 模块的「批量 CAS 认领 + 归属实例取回」模式补建。
 *
 * <p>定时扫描 outbox_event 中 PENDING 事件，反序列化 payload 组装
 * {@link EventEnvelope} 后经 RabbitTemplate 投递 MQ，成功 CAS 置 PUBLISHED；
 * 失败按退避（5s×尝试次数，300s 封顶）推迟重试；超过 {@link #MAX_ATTEMPTS} 次
 * 仍失败的事件 CAS 置 FAILED（终态，人工介入）。</p>
 *
 * <p>事件路由（与 educloud-analytics 动态流队列绑定对齐）：
 * {@code AssignmentGraded} 发布到全域总线 {@code educloud.events}（routing key
 * {@code assignment.graded}，动态流作业队列定向绑定）；其余内容域事件发布到
 * {@code educloud.content.events}（动态流内容队列以 {@code #} 通配绑定），
 * 路由键按事件类型映射为点分小写形式。</p>
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

    /** 事件类型 → 内容域交换机路由键（点分小写，与动态流消费者订阅风格一致）。 */
    private static final Map<String, String> CONTENT_EVENT_ROUTING_KEYS = Map.of(
            "AssignmentSubmitted", "assignment.submitted",
            "CourseCompleted", "course.completed",
            "CertificateIssued", "certificate.issued",
            "ContentRevisionPublished", "content.revision.published");

    /** 作业批改事件：发布在全域总线，路由键与 analytics 动态流作业队列绑定完全一致。 */
    private static final String ASSIGNMENT_GRADED_EVENT_TYPE = "AssignmentGraded";
    private static final String ASSIGNMENT_GRADED_ROUTING_KEY = "assignment.graded";

    /** 考试判分事件：发布在全域总线，路由键与 analytics 动态流考试队列绑定一致。 */
    private static final String EXAM_GRADED_EVENT_TYPE = "ExamGraded";
    private static final String EXAM_GRADED_ROUTING_KEY = "exam.graded";

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

    @Scheduled(fixedDelayString = "${educloud.content.outbox.poll-interval:5000}")
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
                    "educloud-content",
                    event.getSourceSequence(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getAggregateVersion(),
                    event.getOccurredAt() == null
                            ? null
                            : event.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant(),
                    event.getRequestId(),
                    event.getTraceId(),
                    data);
            Route route = routeFor(event.getEventType(), event.getAggregateType(), event.getAggregateId());
            rabbitTemplate.convertAndSend(route.exchange(), route.routingKey(), envelope);
            outboxEventMapper.markPublished(event.getId());
        } catch (Exception failure) {
            int nextAttempt = event.getAttemptCount() + 1;
            // 退避时长交由数据库时钟（NOW(3)）在 SQL 内计算 next_attempt_at，
            // 避免应用本地时区与 DB 时区不一致导致事件被误判为“未来”而永不认领。
            long delaySeconds = Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS * nextAttempt);
            if (nextAttempt >= MAX_ATTEMPTS) {
                outboxEventMapper.markFailed(event.getId(), delaySeconds);
                LOGGER.error("Outbox event {} reached the retry limit and is marked FAILED",
                        event.getEventId(), failure);
            } else {
                outboxEventMapper.markFailedAttempt(event.getId(), delaySeconds);
                LOGGER.warn("Outbox event {} delivery failed, attempt {}",
                        event.getEventId(), nextAttempt, failure);
            }
        }
    }

    /** 事件路由：作业批改走全域总线定向路由，其余内容域事件走内容交换机映射路由。 */
    private Route routeFor(String eventType, String aggregateType, String aggregateId) {
        if (ASSIGNMENT_GRADED_EVENT_TYPE.equals(eventType)) {
            return new Route(RabbitConfiguration.DOMAIN_EVENT_EXCHANGE, ASSIGNMENT_GRADED_ROUTING_KEY);
        }
        if (EXAM_GRADED_EVENT_TYPE.equals(eventType)) {
            return new Route(RabbitConfiguration.DOMAIN_EVENT_EXCHANGE, EXAM_GRADED_ROUTING_KEY);
        }
        String contentRoutingKey = CONTENT_EVENT_ROUTING_KEYS.get(eventType);
        if (contentRoutingKey != null) {
            return new Route(RabbitConfiguration.CONTENT_EVENT_EXCHANGE, contentRoutingKey);
        }
        // 兜底：未映射事件按点分隔 aggregateType.aggregateId 发布到内容交换机。
        return new Route(RabbitConfiguration.CONTENT_EVENT_EXCHANGE, aggregateType + "." + aggregateId);
    }

    private record Route(String exchange, String routingKey) {
    }
}
