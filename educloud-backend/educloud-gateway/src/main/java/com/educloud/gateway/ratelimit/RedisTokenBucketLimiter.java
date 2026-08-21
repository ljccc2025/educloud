package com.educloud.gateway.ratelimit;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public final class RedisTokenBucketLimiter {

    private static final Pattern SAFE_ENVIRONMENT = Pattern.compile("[a-z0-9-]{1,32}");
    private static final Pattern SAFE_DIMENSION = Pattern.compile(
            "ordinary|login-ip|login-account|payment-callback");
    private static final Pattern SAFE_DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(2);
    private static final String SCRIPT_RESOURCE = "com/educloud/gateway/ratelimit/token-bucket.lua";

    private final ReactiveStringRedisTemplate redis;
    private final String keyPrefix;
    private final Duration commandTimeout;
    private final DefaultRedisScript<List> script;

    public RedisTokenBucketLimiter(
            ReactiveStringRedisTemplate redis, GatewayRuntimeProperties runtimeProperties) {
        this(redis, runtimeProperties, DEFAULT_COMMAND_TIMEOUT);
    }

    RedisTokenBucketLimiter(
            ReactiveStringRedisTemplate redis,
            GatewayRuntimeProperties runtimeProperties,
            Duration commandTimeout) {
        this.redis = Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(runtimeProperties, "runtimeProperties");
        String environment = runtimeProperties.environment();
        if (environment == null || !SAFE_ENVIRONMENT.matcher(environment).matches()) {
            throw new IllegalArgumentException("environment must match [a-z0-9-]{1,32}");
        }
        this.keyPrefix = "educloud:{" + environment + ":ratelimit}:";
        this.commandTimeout = Objects.requireNonNull(commandTimeout, "commandTimeout");
        if (commandTimeout.isZero() || commandTimeout.isNegative()) {
            throw new IllegalArgumentException("commandTimeout must be positive");
        }
        this.script = tokenBucketScript();
    }

    public BucketRequest bucket(String dimension, String digest, BucketRule rule) {
        if (dimension == null || !SAFE_DIMENSION.matcher(dimension).matches()
                || digest == null || !SAFE_DIGEST.matcher(digest).matches()) {
            throw new IllegalArgumentException("invalid rate-limit bucket identity");
        }
        return new BucketRequest(keyPrefix + dimension + ":" + digest, rule);
    }

    public Mono<RateLimitDecision> acquire(List<BucketRequest> buckets) {
        if (buckets == null || buckets.isEmpty() || buckets.size() > 4) {
            return Mono.error(new RateLimitDependencyException());
        }
        List<BucketRequest> immutable = List.copyOf(buckets);
        if (new HashSet<>(immutable.stream().map(BucketRequest::key).toList()).size() != immutable.size()
                || immutable.stream().anyMatch(bucket -> !bucket.key().startsWith(keyPrefix))) {
            return Mono.error(new RateLimitDependencyException());
        }

        List<String> keys = immutable.stream().map(BucketRequest::key).toList();
        List<String> arguments = new ArrayList<>(immutable.size() * 3);
        for (BucketRequest bucket : immutable) {
            arguments.add(Long.toString(bucket.rule().requests()));
            arguments.add(Long.toString(bucket.rule().period().toMillis()));
            arguments.add(Long.toString(bucket.rule().burst()));
        }

        return Mono.defer(() -> redis.execute(script, keys, arguments).next())
                .switchIfEmpty(Mono.error(new RateLimitDependencyException()))
                .map(RedisTokenBucketLimiter::parseDecision)
                .timeout(commandTimeout)
                .onErrorMap(error -> error instanceof RateLimitDependencyException
                        ? error : new RateLimitDependencyException());
    }

    private static RateLimitDecision parseDecision(List<?> result) {
        if (result == null || result.size() != 2) {
            throw new RateLimitDependencyException();
        }
        Long allowed = integer(result.get(0));
        Long retryMillis = integer(result.get(1));
        if (Long.valueOf(1).equals(allowed) && Long.valueOf(0).equals(retryMillis)) {
            return new RateLimitDecision(true, Duration.ZERO);
        }
        if (Long.valueOf(0).equals(allowed) && retryMillis != null && retryMillis > 0) {
            return new RateLimitDecision(false, Duration.ofMillis(retryMillis));
        }
        throw new RateLimitDependencyException();
    }

    private static Long integer(Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        String text;
        if (value instanceof String string) {
            text = string;
        } else if (value instanceof byte[] bytes) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            return null;
        }
        if (!text.matches("[0-9]+")) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_RESOURCE));
        script.setResultType(List.class);
        return script;
    }

    public static final class RateLimitDependencyException extends RuntimeException {

        public RateLimitDependencyException() {
            super("rate-limit dependency is unavailable");
        }
    }
}
