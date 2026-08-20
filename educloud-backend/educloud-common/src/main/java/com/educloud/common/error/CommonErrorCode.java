package com.educloud.common.error;

public enum CommonErrorCode implements ErrorCode {
    VALIDATION_FAILED(400, "Validation failed"),
    UNAUTHENTICATED(401, "Authentication required"),
    ACCESS_DENIED(403, "Access denied"),
    VERSION_CONFLICT(409, "Resource version conflict"),
    RATE_LIMITED(429, "Too many requests"),
    DEPENDENCY_UNAVAILABLE(503, "Dependency unavailable"),
    INTERNAL_ERROR(500, "Internal server error");

    private final int httpStatus;
    private final String defaultMessage;

    CommonErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
