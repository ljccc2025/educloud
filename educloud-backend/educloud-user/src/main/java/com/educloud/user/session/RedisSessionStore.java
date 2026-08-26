package com.educloud.user.session;

import com.educloud.user.config.SessionProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    /** 原子写入脚本：HSET + EXPIRE 一次执行，避免进程崩溃/连接中断产生无 TTL 的会话键（BUG-039）。 */
    private static final DefaultRedisScript<Long> WRITE_ACTIVE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('HSET', KEYS[1], 'subject', ARGV[1], 'status', 'ACTIVE', 'tokenVersion', ARGV[2]); "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[3]); return 1",
            Long.class);

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
        // 原子写入：HSET + EXPIRE 在单个 Lua 脚本中执行，避免非原子写导致无 TTL 会话键
        redis.execute(WRITE_ACTIVE_SCRIPT,
                java.util.List.of(key),
                Objects.requireNonNull(subject, "subject"),
                Long.toString(tokenVersion),
                Long.toString(requirePositive(ttl).getSeconds()));
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