package com.educloud.gateway.ratelimit;

import java.util.Objects;
import java.util.regex.Pattern;

public record BucketRequest(String key, BucketRule rule) {

    private static final Pattern SAFE_KEY = Pattern.compile(
            "educloud:\\{[a-z0-9-]{1,32}:ratelimit}:"
                    + "(?:ordinary|login-ip|login-account|payment-callback):[0-9a-f]{64}");

    public BucketRequest {
        Objects.requireNonNull(rule, "rule");
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid rate-limit bucket key");
        }
    }
}
