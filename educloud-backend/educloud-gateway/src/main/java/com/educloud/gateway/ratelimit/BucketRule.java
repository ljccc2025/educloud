package com.educloud.gateway.ratelimit;

import java.time.Duration;
import java.util.Objects;

public record BucketRule(long requests, Duration period, long burst) {

    public BucketRule {
        Objects.requireNonNull(period, "period");
        if (requests <= 0 || burst <= 0 || burst < requests) {
            throw new IllegalArgumentException("bucket requests and burst must define a positive capacity");
        }
        if (period.isZero() || period.isNegative() || period.toMillis() <= 0) {
            throw new IllegalArgumentException("bucket period must be at least one millisecond");
        }
    }
}
