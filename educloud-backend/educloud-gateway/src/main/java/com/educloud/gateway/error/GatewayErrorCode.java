package com.educloud.gateway.error;

import com.educloud.common.error.ErrorCode;

public enum GatewayErrorCode implements ErrorCode {

    GATEWAY_BAD_REQUEST(400, "Bad gateway request"),
    UNAUTHENTICATED(401, "Authentication required"),
    ACCESS_DENIED(403, "Access denied"),
    GATEWAY_ROUTE_NOT_FOUND(404, "Route not found"),
    GATEWAY_REQUEST_TOO_LARGE(413, "Request is too large"),
    GATEWAY_UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    RATE_LIMITED(429, "Too many requests"),
    DEPENDENCY_UNAVAILABLE(503, "Dependency unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway timeout"),
    INTERNAL_ERROR(500, "Internal server error");

    private final int httpStatus;
    private final String defaultMessage;

    GatewayErrorCode(int httpStatus, String defaultMessage) {
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
