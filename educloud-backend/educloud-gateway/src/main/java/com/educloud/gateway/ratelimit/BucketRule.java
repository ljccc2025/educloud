package com.educloud.gateway.ratelimit;

import java.time.Duration;
import java.util.Objects;

public record BucketRule(long requests, Duration period, long burst) {

    private static final long MAX_CAPACITY = 1_000_000;
    private static final Duration MIN_PERIOD = Duration.ofMillis(1);
    private static final Duration MAX_PERIOD = Duration.ofHours(24);
    private static final Duration MAX_EXPIRY = Duration.ofDays(7);

    public BucketRule {
        Objects.requireNonNull(period, "period");
        if (requests <= 0
                || requests > MAX_CAPACITY
                || burst <= 0
                || burst > MAX_CAPACITY
                || burst < requests
                || period.compareTo(MIN_PERIOD) < 0
                || period.compareTo(MAX_PERIOD) > 0
                || derivedExpiryMillis(requests, period, burst) > MAX_EXPIRY.toMillis()) {
            throw new IllegalArgumentException("bucket rule is outside the configured safety bounds");
        }
    }

    private static long derivedExpiryMillis(long requests, Duration period, long burst) {
        long periodMillis = period.toMillis();
        return (periodMillis * burst + requests - 1) / requests;
    }
}
