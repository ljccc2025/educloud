package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public final class RedisSessionVerifier implements SessionVerifier {

    private static final Pattern SAFE_ENVIRONMENT = Pattern.compile("[a-z0-9-]{1,32}");
    private static final Pattern SAFE_CLAIM = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(2);
    private static final String SCRIPT_RESOURCE = "com/educloud/gateway/security/check-session.lua";

    private final ReactiveStringRedisTemplate redis;
    private final String environment;
    private final Duration commandTimeout;
    private final DefaultRedisScript<List> checkSessionScript;

    @Autowired
    public RedisSessionVerifier(
            ReactiveStringRedisTemplate redis, GatewayRuntimeProperties runtimeProperties) {
        this(redis, runtimeProperties, DEFAULT_COMMAND_TIMEOUT);
    }

    RedisSessionVerifier(
            ReactiveStringRedisTemplate redis,
            GatewayRuntimeProperties runtimeProperties,
            Duration commandTimeout) {
        this.redis = Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(runtimeProperties, "runtimeProperties");
        this.environment = requireEnvironment(runtimeProperties.environment());
        this.commandTimeout = requirePositive(commandTimeout);
        this.checkSessionScript = sessionScript();
    }

    @Override
    public Mono<SessionCheckResult> verify(String subject, String sessionId, long tokenVersion) {
        if (!isSafeClaim(subject) || !isSafeClaim(sessionId) || tokenVersion < 0) {
            return Mono.just(SessionCheckResult.CORRUPT);
        }

        String key = "educloud:{" + environment + ":auth}:session:" + sessionId;
        return Mono.defer(() -> redis.execute(checkSessionScript, List.of(key), List.of())
                        .next()
                        .map(result -> classify(result, subject, tokenVersion))
                        .defaultIfEmpty(SessionCheckResult.CORRUPT))
                .timeout(commandTimeout)
                .onErrorReturn(SessionCheckResult.DEPENDENCY_ERROR);
    }

    private static SessionCheckResult classify(
            List<?> result, String expectedSubject, long expectedVersion) {
        if (result == null || result.isEmpty()) {
            return SessionCheckResult.CORRUPT;
        }

        Long exists = integer(result.get(0));
        if (Long.valueOf(0).equals(exists)) {
            return result.size() == 1 ? SessionCheckResult.MISSING : SessionCheckResult.CORRUPT;
        }
        if (!Long.valueOf(1).equals(exists) || result.size() != 5) {
            return SessionCheckResult.CORRUPT;
        }

        String storedSubject = text(result.get(1));
        String status = text(result.get(2));
        Long storedVersion = integer(result.get(3));
        Long pttl = integer(result.get(4));
        if (!isSafeClaim(storedSubject)
                || status == null
                || status.isBlank()
                || storedVersion == null
                || storedVersion < 0
                || pttl == null
                || pttl <= 0) {
            return SessionCheckResult.CORRUPT;
        }
        if ("REVOKED".equals(status)) {
            return SessionCheckResult.REVOKED;
        }
        if (!"ACTIVE".equals(status)) {
            return SessionCheckResult.CORRUPT;
        }
        if (!storedSubject.equals(expectedSubject)) {
            return SessionCheckResult.SUBJECT_MISMATCH;
        }
        if (storedVersion != expectedVersion) {
            return SessionCheckResult.VERSION_MISMATCH;
        }
        return SessionCheckResult.ACTIVE;
    }

    private static boolean isSafeClaim(String value) {
        return value != null && SAFE_CLAIM.matcher(value).matches();
    }

    private static String text(Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static Long integer(Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException ignored) {
                return null;
            }
        }
        String text = text(value);
        if (text == null || !text.matches("[0-9]+")) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String requireEnvironment(String environment) {
        if (environment == null || !SAFE_ENVIRONMENT.matcher(environment).matches()) {
            throw new IllegalArgumentException("environment must match [a-z0-9-]{1,32}");
        }
        return environment;
    }

    private static Duration requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "commandTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("commandTimeout must be positive");
        }
        return timeout;
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> sessionScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_RESOURCE));
        script.setResultType(List.class);
        return script;
    }
}
