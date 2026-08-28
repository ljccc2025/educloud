package com.educloud.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;

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

    /**
     * 事件负载节点：经 envelope 包装时业务字段在 data 子节点内，
     * 未包装的扁平结构原样返回。供消费端统一解包，避免各模块重复实现。
     */
    public static JsonNode payloadNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode data = root.get("data");
        return data != null && data.isObject() ? data : root;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
