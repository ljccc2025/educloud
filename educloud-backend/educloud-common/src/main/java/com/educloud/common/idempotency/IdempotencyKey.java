package com.educloud.common.idempotency;

import java.util.Objects;

public record IdempotencyKey(
        String actorId,
        String operation,
        String key,
        String requestDigest) {

    public IdempotencyKey {
        actorId = requireText(actorId, "actorId");
        operation = requireText(operation, "operation");
        key = requireText(key, "key");
        requestDigest = requireText(requestDigest, "requestDigest");
    }

    public boolean sameScope(IdempotencyKey other) {
        Objects.requireNonNull(other, "other");
        return actorId.equals(other.actorId)
                && operation.equals(other.operation)
                && key.equals(other.key);
    }

    public boolean representsSameRequest(IdempotencyKey other) {
        return sameScope(other) && requestDigest.equals(other.requestDigest);
    }

    public boolean conflictsWith(IdempotencyKey other) {
        return sameScope(other) && !requestDigest.equals(other.requestDigest);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
