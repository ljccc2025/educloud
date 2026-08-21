package com.educloud.gateway.ratelimit;

import java.time.Duration;
import java.util.Objects;

public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    public RateLimitDecision {
        Objects.requireNonNull(retryAfter, "retryAfter");
        if ((allowed && !retryAfter.isZero())
                || (!allowed && (retryAfter.isZero() || retryAfter.isNegative()))) {
            throw new IllegalArgumentException("retryAfter must be zero only for allowed decisions");
        }
    }
}
