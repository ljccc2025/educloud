package com.educloud.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.search.entity.IndexSyncFailureEntity;
import com.educloud.search.mapper.IndexSyncFailureMapper;
import com.educloud.search.messaging.event.ContentDomainEvent;
import com.educloud.search.messaging.event.CourseDomainEvent;
import com.educloud.search.service.DlqRecoveryService;
import com.educloud.search.service.IndexSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 索引同步死信恢复服务实现类
 * <p>
 * 职责：
 * 1. DLQ 消费者调用 {@link #recordFailure(Message)} 将死信落库为 PENDING 记录（含原始消息、失败原因、重试次数）；
 * 2. {@link #scheduledReplay()} 每 5 分钟扫描 PENDING 记录，直接调用 {@link IndexSyncService} 重放
 * （复用其幂等/版本校验，无需重新投递 MQ 队列），成功标 RESOLVED，连续失败超 5 次标 DEAD（仅告警日志）；
 * 3. {@link #replayById(Long)} 支持运维手动重放单条记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqRecoveryServiceImpl implements DlqRecoveryService {

    /** 连续重放失败超过该次数后标记 DEAD，不再自动重试 */
    public static final int MAX_RETRY_COUNT = 5;

    /** 单轮定时重放最多处理的记录数，防止单轮任务过载 */
    private static final int REPLAY_BATCH_LIMIT = 100;

    /** 失败原因列最大长度（对应表 error VARCHAR(1024)） */
    private static final int MAX_ERROR_LENGTH = 1024;

    private final IndexSyncFailureMapper failureMapper;
    private final IndexSyncService indexSyncService;
    private final ObjectMapper objectMapper;

    /** 防止定时任务与手动重放并发执行导致同一记录被重复处理 */
    private final AtomicBoolean replayRunning = new AtomicBoolean(false);

    @Override
    public void recordFailure(Message message) {
        if (message == null || message.getBody() == null) {
            log.warn("Skip recording null/empty dead-letter message");
            return;
        }
        try {
            MessageProperties props = message.getMessageProperties();
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);

            // 从 x-death header 解析原始交换机/路由键与死信次数
            String exchange = "";
            String routingKey = "";
            int deathCount = 0;
            Object xDeath = props != null ? props.getHeader("x-death") : null;
            if (xDeath instanceof List<?> deaths && !deaths.isEmpty()) {
                Object last = deaths.get(deaths.size() - 1);
                if (last instanceof Map<?, ?> death) {
                    if (death.get("exchange") != null) {
                        exchange = String.valueOf(death.get("exchange"));
                    }
                    Object routingKeys = death.get("routing-keys");
                    if (routingKeys instanceof List<?> rkList && !rkList.isEmpty()) {
                        routingKey = String.valueOf(rkList.get(0));
                    }
                    if (death.get("count") instanceof Number count) {
                        deathCount = count.intValue();
                    }
                }
            }

            String error = props != null && props.getHeader("x-exception-message") != null
                    ? String.valueOf(props.getHeader("x-exception-message"))
                    : "Dead-lettered from " + (props != null && props.getReceivedRoutingKey() != null
                            ? props.getReceivedRoutingKey() : "unknown-routing-key");

            String messageId = resolveMessageId(payload);
            LocalDateTime now = LocalDateTime.now();
            IndexSyncFailureEntity existing = findExistingByMessageId(messageId);

            if (existing != null) {
                // 同消息再次死信：更新载荷/原因/时间，DEAD 记录不复活，其余回到 PENDING 待重放
                existing.setPayload(payload);
                existing.setError(truncate(error));
                existing.setOccurredAt(now);
                existing.setUpdatedAt(now);
                existing.setRetryCount(Math.max(
                        existing.getRetryCount() != null ? existing.getRetryCount() : 0, deathCount));
                if (!STATUS_DEAD.equals(existing.getStatus())) {
                    existing.setStatus(STATUS_PENDING);
                }
                failureMapper.updateById(existing);
                log.warn("Updated dead-letter failure record: messageId={}, exchange={}, routingKey={}, deathCount={}",
                        messageId, exchange, routingKey, deathCount);
            } else {
                IndexSyncFailureEntity entity = IndexSyncFailureEntity.builder()
                        .messageId(messageId)
                        .exchange(exchange)
                        .routingKey(routingKey)
                        .payload(payload)
                        .error(truncate(error))
                        .retryCount(deathCount)
                        .status(STATUS_PENDING)
                        .occurredAt(now)
                        .updatedAt(now)
                        .build();
                failureMapper.insert(entity);
                log.warn("Recorded dead-letter failure: id={}, messageId={}, exchange={}, routingKey={}, deathCount={}",
                        entity.getId(), messageId, exchange, routingKey, deathCount);
            }
        } catch (Exception e) {
            // 落库失败仅记录 ERROR 告警，绝不抛出（避免 DLQ 消息无限循环）
            log.error("Failed to persist dead-letter message to index_sync_failure: {}", e.getMessage(), e);
        }
    }

    @Override
    public int replayPending() {
        if (!replayRunning.compareAndSet(false, true)) {
            log.info("DLQ replay task already running, skip this cycle");
            return 0;
        }
        try {
            List<IndexSyncFailureEntity> pending = failureMapper.selectList(
                    new LambdaQueryWrapper<IndexSyncFailureEntity>()
                            .eq(IndexSyncFailureEntity::getStatus, STATUS_PENDING)
                            .orderByAsc(IndexSyncFailureEntity::getOccurredAt)
                            .last("LIMIT " + REPLAY_BATCH_LIMIT));
            if (pending.isEmpty()) {
                return 0;
            }
            int settled = 0;
            for (IndexSyncFailureEntity record : pending) {
                ReplayResult result = replayOne(record);
                if (result != null && !STATUS_PENDING.equals(result.status())) {
                    settled++;
                }
            }
            log.info("DLQ replay cycle finished: processed={}, settled(resolved/dead)={}", pending.size(), settled);
            return settled;
        } finally {
            replayRunning.set(false);
        }
    }

    /**
     * 定时重放任务：每 5 分钟扫描 PENDING 失败记录
     */
    @Scheduled(fixedDelayString = "${educloud.search.dlq.replay-interval-ms:300000}",
            initialDelayString = "${educloud.search.dlq.replay-initial-delay-ms:60000}")
    public void scheduledReplay() {
        try {
            replayPending();
        } catch (Exception e) {
            log.error("Scheduled DLQ replay failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public ReplayResult replayById(Long id) {
        if (id == null) {
            return new ReplayResult(null, "NOT_FOUND", "重放参数 id 不能为空");
        }
        IndexSyncFailureEntity record = failureMapper.selectById(id);
        if (record == null) {
            log.warn("Manual replay failed: record not found, id={}", id);
            return new ReplayResult(id, "NOT_FOUND", "失败记录不存在: id=" + id);
        }
        return replayOne(record);
    }

    @Override
    public List<ReplayResult> listRecentFailures(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<IndexSyncFailureEntity> records = failureMapper.selectList(
                new LambdaQueryWrapper<IndexSyncFailureEntity>()
                        .orderByDesc(IndexSyncFailureEntity::getOccurredAt)
                        .last("LIMIT " + safeLimit));
        List<ReplayResult> results = new ArrayList<>(records.size());
        for (IndexSyncFailureEntity record : records) {
            results.add(new ReplayResult(record.getId(), record.getStatus(),
                    record.getMessageId() + " / " + (record.getError() != null ? record.getError() : "")));
        }
        return results;
    }

    /**
     * 重放单条失败记录：成功标 RESOLVED；失败累加 retryCount，超过 {@link #MAX_RETRY_COUNT} 标 DEAD
     */
    private ReplayResult replayOne(IndexSyncFailureEntity record) {
        String payload = record.getPayload();
        String routingKey = record.getRoutingKey();
        try {
            Object event = deserializeEvent(payload, routingKey);
            if (event instanceof CourseDomainEvent courseEvent) {
                indexSyncService.handleCourseEvent(courseEvent);
            } else if (event instanceof ContentDomainEvent contentEvent) {
                indexSyncService.handleContentEvent(contentEvent);
            } else {
                throw new IllegalArgumentException("无法识别死信事件类型: routingKey=" + routingKey);
            }
            record.setStatus(STATUS_RESOLVED);
            record.setUpdatedAt(LocalDateTime.now());
            failureMapper.updateById(record);
            log.info("Dead-letter message replayed successfully: id={}, messageId={}", record.getId(), record.getMessageId());
            return new ReplayResult(record.getId(), STATUS_RESOLVED, "重放成功");
        } catch (Exception e) {
            int retries = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
            record.setRetryCount(retries);
            record.setError(truncate(e.getMessage()));
            record.setUpdatedAt(LocalDateTime.now());
            if (retries >= MAX_RETRY_COUNT) {
                record.setStatus(STATUS_DEAD);
                log.error("Dead-letter message exceeded max retries ({}), marked DEAD: id={}, messageId={}, error={}",
                        MAX_RETRY_COUNT, record.getId(), record.getMessageId(), e.getMessage(), e);
            } else {
                record.setStatus(STATUS_PENDING);
                log.warn("Replay failed for dead-letter message: id={}, messageId={}, retryCount={}, error={}",
                        record.getId(), record.getMessageId(), retries, e.getMessage());
            }
            failureMapper.updateById(record);
            return new ReplayResult(record.getId(), record.getStatus(), "重放失败: " + e.getMessage());
        }
    }

    private IndexSyncFailureEntity findExistingByMessageId(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }
        List<IndexSyncFailureEntity> list = failureMapper.selectList(
                new LambdaQueryWrapper<IndexSyncFailureEntity>()
                        .eq(IndexSyncFailureEntity::getMessageId, messageId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 从死信载荷解析消息唯一 ID，兼容 messageId/eventId/id 字段
     */
    private String resolveMessageId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (String field : new String[]{"messageId", "eventId", "id"}) {
                String id = root.path(field).asText("");
                if (StringUtils.hasText(id)) {
                    return id;
                }
            }
        } catch (Exception ignored) {
            // 载荷非 JSON 时回退
        }
        return "UNKNOWN_" + System.currentTimeMillis();
    }

    /**
     * 根据路由键与载荷 aggregateType 判断事件类型并反序列化
     */
    private Object deserializeEvent(String payload, String routingKey) throws Exception {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("死信消息载荷为空");
        }
        boolean contentEvent = routingKey != null && routingKey.toLowerCase().startsWith("content");
        if (!contentEvent) {
            JsonNode root = objectMapper.readTree(payload);
            String aggregateType = root.path("aggregateType").asText("");
            contentEvent = "CourseContent".equalsIgnoreCase(aggregateType)
                    || "Lesson".equalsIgnoreCase(aggregateType)
                    || "Chapter".equalsIgnoreCase(aggregateType)
                    || "Content".equalsIgnoreCase(aggregateType);
        }
        if (contentEvent) {
            return objectMapper.readValue(payload, ContentDomainEvent.class);
        }
        return objectMapper.readValue(payload, CourseDomainEvent.class);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }
}
