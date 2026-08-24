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
        event.setNextAttemptAt(now);
        outboxEventMapper.insert(event);
    }
}
