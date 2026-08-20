package com.educloud.common.api;

import java.time.Instant;
import java.util.Objects;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp) {

    public ApiResponse {
        code = requireText(code, "code");
        message = Objects.requireNonNull(message, "message");
        requestId = requireText(requestId, "requestId");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
