package com.educloud.content.messaging;

import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.entity.OutboxEventEntity;
import com.educloud.content.mapper.OutboxEventMapper;
import com.educloud.content.mapper.OutboxSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private static final String SOURCE_NAME = "educloud-content";

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxSequenceMapper outboxSequenceMapper;
    private final IdentifierGenerator idGenerator;

    @Transactional
    public void write(
            String aggregateType,
            String aggregateId,
            String eventType,
            int eventVersion,
            long aggregateVersion,
            String payloadJson,
            String requestId,
            String traceId) {
        int updated = outboxSequenceMapper.increment(SOURCE_NAME);
        if (updated != 1) {
            throw new IllegalStateException("Outbox sequence row missing for " + SOURCE_NAME);
        }
        Long sequence = outboxSequenceMapper.selectValue(SOURCE_NAME);
        LocalDateTime now = LocalDateTime.now();

        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(idGenerator.nextId());
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setEventVersion(eventVersion);
        event.setAggregateVersion(aggregateVersion);
        event.setPayloadJson(payloadJson);
        event.setRequestId(requestId == null ? "unavailable" : requestId);
        event.setTraceId(traceId);
        event.setOccurredAt(now);
        event.setSourceSequence(sequence);
        event.setPublishStatus("PENDING");
        event.setAttemptCount(0);
        // next_attempt_at 置 NULL 表示“立即可认领”。若写入应用本地时间（LocalDateTime.now()），
        // 当应用时区（CST）与 MySQL 容器时区（UTC）不一致时，新事件会被认领条件
        // {@code next_attempt_at <= NOW()} 误判为“未来 8 小时”而永不投递。
        event.setNextAttemptAt(null);
        outboxEventMapper.insert(event);
    }
}
