package com.educloud.common.error;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final ErrorDetails details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public BusinessException(ErrorCode errorCode, String message, ErrorDetails details) {
        this(errorCode, message, details, null);
    }

    public BusinessException(
            ErrorCode errorCode,
            String message,
            ErrorDetails details,
            Throwable cause) {
        super(requireMessage(message), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorDetails details() {
        return details;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message;
    }
}
