package com.educloud.gateway.error;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class GatewayFailure {

    public enum Category {
        BAD_REQUEST,
        AUTHENTICATION,
        AUTHORIZATION,
        ROUTE,
        REQUEST_SIZE,
        MEDIA_TYPE,
        RATE_LIMIT,
        DEPENDENCY,
        TIMEOUT,
        INTERNAL
    }

    private final GatewayErrorCode code;
    private final String publicMessage;
    private final Duration retryAfter;
    private final Category category;

    private GatewayFailure(GatewayErrorCode code, Duration retryAfter, Category category) {
        this.code = Objects.requireNonNull(code, "code");
        this.publicMessage = code.defaultMessage();
        this.category = Objects.requireNonNull(category, "category");
        if (retryAfter != null && (retryAfter.isZero() || retryAfter.isNegative())) {
            throw new IllegalArgumentException("retryAfter must be positive");
        }
        this.retryAfter = retryAfter;
    }

    public static GatewayFailure of(GatewayErrorCode code) {
        return new GatewayFailure(code, null, categoryFor(code));
    }

    public static GatewayFailure of(GatewayErrorCode code, Category category) {
        return new GatewayFailure(code, null, category);
    }

    public static GatewayFailure rateLimited(Duration retryAfter) {
        return new GatewayFailure(GatewayErrorCode.RATE_LIMITED,
                Objects.requireNonNull(retryAfter, "retryAfter"), Category.RATE_LIMIT);
    }

    public GatewayErrorCode code() {
        return code;
    }

    public String publicMessage() {
        return publicMessage;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public Category category() {
        return category;
    }

    private static Category categoryFor(GatewayErrorCode code) {
        return switch (code) {
            case GATEWAY_BAD_REQUEST -> Category.BAD_REQUEST;
            case UNAUTHENTICATED -> Category.AUTHENTICATION;
            case ACCESS_DENIED -> Category.AUTHORIZATION;
            case GATEWAY_ROUTE_NOT_FOUND -> Category.ROUTE;
            case GATEWAY_REQUEST_TOO_LARGE -> Category.REQUEST_SIZE;
            case GATEWAY_UNSUPPORTED_MEDIA_TYPE -> Category.MEDIA_TYPE;
            case RATE_LIMITED -> Category.RATE_LIMIT;
            case DEPENDENCY_UNAVAILABLE -> Category.DEPENDENCY;
            case GATEWAY_TIMEOUT -> Category.TIMEOUT;
            case INTERNAL_ERROR -> Category.INTERNAL;
        };
    }
}
