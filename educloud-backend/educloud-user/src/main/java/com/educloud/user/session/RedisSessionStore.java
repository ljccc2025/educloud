package com.educloud.user.session;

import com.educloud.user.config.SessionProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 基于 StringRedisTemplate 的会话读模型实现。
 * 字段值与 Gateway RedisSessionVerifier/check-session.lua 完全一致：status 只写 ACTIVE/REVOKED，
 * tokenVersion 为字符串数字，TTL 必须为正。
 */
@Component
public final class RedisSessionStore implements SessionStore {

    private static final Pattern SAFE_ENVIRONMENT = Pattern.compile("[a-z0-9-]{1,32}");
    private static final String KEY_PREFIX = "educloud:{";
    private static final String KEY_SUFFIX = ":auth}:session:";

    private final StringRedisTemplate redis;
    private final String environment;

    public RedisSessionStore(StringRedisTemplate redis, SessionProperties properties) {
        this.redis = Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(properties, "properties");
        String environment = properties.environment();
        if (environment == null || !SAFE_ENVIRONMENT.matcher(environment).matches()) {
            throw new IllegalArgumentException("session environment must match [a-z0-9-]{1,32}");
        }
        this.environment = environment;
    }

    @Override
    public void writeActive(String sessionId, String subject, long tokenVersion, Duration ttl) {
        String key = key(sessionId);
        redis.opsForHash().putAll(key, Map.of(
                "subject", Objects.requireNonNull(subject, "subject"),
                "status", "ACTIVE",
                "tokenVersion", Long.toString(tokenVersion)));
        redis.expire(key, requirePositive(ttl));
    }

    @Override
    public void markRevoked(String sessionId, Duration ttl) {
        String key = key(sessionId);
        redis.opsForHash().put(key, "status", "REVOKED");
        redis.expire(key, requirePositive(ttl));
    }

    @Override
    public java.util.Optional<SessionSnapshot> read(String sessionId) {
        String key = key(sessionId);
        java.util.Map<Object, Object> values = redis.opsForHash().entries(key);
        if (values.isEmpty()) {
            return java.util.Optional.empty();
        }
        Object status = values.get("status");
        Object tokenVersion = values.get("tokenVersion");
        if (!(status instanceof String statusText) || !(tokenVersion instanceof String versionText)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new SessionSnapshot(
                    statusText, Long.parseLong(versionText)));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    private String key(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return KEY_PREFIX + environment + KEY_SUFFIX + sessionId;
    }

    private static Duration requirePositive(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("session ttl must be positive");
        }
        return ttl;
    }
}