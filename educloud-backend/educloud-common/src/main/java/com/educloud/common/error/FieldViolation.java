package com.educloud.common.error;

import java.util.Objects;

public record FieldViolation(String field, String code, String message) {

    public FieldViolation {
        field = requireText(field, "field");
        code = requireText(code, "code");
        message = Objects.requireNonNull(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
