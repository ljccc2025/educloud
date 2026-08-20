package com.educloud.common.messaging;

import java.time.Instant;
import java.util.Objects;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        String sourceService,
        long sourceSequence,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String requestId,
        String traceId,
        T data) {

    public EventEnvelope {
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        sourceService = requireText(sourceService, "sourceService");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        requestId = requireText(requestId, "requestId");
        if (traceId != null) {
            traceId = requireText(traceId, "traceId");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        if (sourceSequence < 0) {
            throw new IllegalArgumentException("sourceSequence must not be negative");
        }
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        data = Objects.requireNonNull(data, "data");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
