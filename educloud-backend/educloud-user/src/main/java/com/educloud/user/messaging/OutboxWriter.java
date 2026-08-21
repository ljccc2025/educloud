package com.educloud.user.messaging;

import com.educloud.user.entity.OutboxEventEntity;
import com.educloud.user.mapper.OutboxEventMapper;
import com.educloud.user.mapper.OutboxSequenceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 业务事务内写入 Outbox（同一本地事务提交，数据设计第 14 节/可靠性设计第 4.1 节）。
 * source_sequence 由 outbox_sequence 单条原子 UPDATE 递增，保证按提交顺序单调。
 */
@Component
public final class OutboxWriter {

    private static final String SOURCE_NAME = "educloud-user";

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxSequenceMapper outboxSequenceMapper;

    public OutboxWriter(OutboxEventMapper outboxEventMapper, OutboxSequenceMapper outboxSequenceMapper) {
        this.outboxEventMapper = Objects.requireNonNull(outboxEventMapper, "outboxEventMapper");
        this.outboxSequenceMapper = Objects.requireNonNull(outboxSequenceMapper, "outboxSequenceMapper");
    }

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
            throw new IllegalStateException("outbox sequence row is missing for " + SOURCE_NAME);
        }
        long sequence = outboxSequenceMapper.selectValue(SOURCE_NAME);
        Instant now = Instant.now();
        OutboxEventEntity event = new OutboxEventEntity();
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
        event.setNextAttemptAt(now);
        outboxEventMapper.insert(event);
    }
}
