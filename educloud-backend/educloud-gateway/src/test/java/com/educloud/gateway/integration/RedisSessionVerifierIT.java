package com.educloud.gateway.integration;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import com.educloud.gateway.security.RedisSessionVerifier;
import com.educloud.gateway.security.SessionCheckResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisSessionVerifierIT {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.2.5-alpine");
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String SUBJECT = "user-redis-it";
    private static final long TOKEN_VERSION = 7L;

    private final String environment = environment();
    private GenericContainer<?> redisContainer;
    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redis;
    private RedisSessionVerifier verifier;

    @BeforeAll
    void startRedis() {
        redisContainer = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
        redisContainer.start();
        connectionFactory = connectionFactory(redisContainer);
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        verifier = new RedisSessionVerifier(redis, new GatewayRuntimeProperties(environment));
    }

    @AfterEach
    void removeOwnedKeys() {
        List<String> keys = ownedKeys();
        if (!keys.isEmpty()) {
            redis.delete(reactor.core.publisher.Flux.fromIterable(keys)).block(BLOCK_TIMEOUT);
        }
        assertThat(ownedKeys()).as("owned Redis session keys after cleanup").isEmpty();
    }

    @AfterAll
    void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @Test
    void classifiesActiveMissingRevokedAndClaimMismatchesAgainstRealRedis() {
        String sessionId = "session-classification";

        writeSession(sessionId, SUBJECT, "ACTIVE", TOKEN_VERSION, Duration.ofMinutes(1));
        assertThat(verify(sessionId)).isEqualTo(SessionCheckResult.ACTIVE);

        redis.delete(key(sessionId)).block(BLOCK_TIMEOUT);
        assertThat(verify(sessionId)).isEqualTo(SessionCheckResult.MISSING);

        writeSession(sessionId, SUBJECT, "REVOKED", TOKEN_VERSION, Duration.ofMinutes(1));
        assertThat(verify(sessionId)).isEqualTo(SessionCheckResult.REVOKED);

        writeSession(sessionId, "another-user", "ACTIVE", TOKEN_VERSION, Duration.ofMinutes(1));
        assertThat(verify(sessionId)).isEqualTo(SessionCheckResult.SUBJECT_MISMATCH);

        writeSession(sessionId, SUBJECT, "ACTIVE", TOKEN_VERSION + 1, Duration.ofMinutes(1));
        assertThat(verify(sessionId)).isEqualTo(SessionCheckResult.VERSION_MISMATCH);
    }

    @Test
    void rejectsMalformedMissingTtlAndExpiredSessionState() {
        String malformed = "session-malformed";
        redis.opsForHash().putAll(key(malformed), Map.of(
                "subject", SUBJECT,
                "status", "UNKNOWN",
                "tokenVersion", Long.toString(TOKEN_VERSION))).block(BLOCK_TIMEOUT);
        redis.expire(key(malformed), Duration.ofMinutes(1)).block(BLOCK_TIMEOUT);
        assertThat(verify(malformed)).isEqualTo(SessionCheckResult.CORRUPT);

        String noTtl = "session-no-ttl";
        redis.opsForHash().putAll(key(noTtl), Map.of(
                "subject", SUBJECT,
                "status", "ACTIVE",
                "tokenVersion", Long.toString(TOKEN_VERSION))).block(BLOCK_TIMEOUT);
        assertThat(verify(noTtl)).isEqualTo(SessionCheckResult.CORRUPT);

        String expired = "session-expired";
        writeSession(expired, SUBJECT, "ACTIVE", TOKEN_VERSION, Duration.ofMillis(80));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(
                () -> assertThat(verify(expired)).isEqualTo(SessionCheckResult.MISSING));
    }

    @Test
    void mapsAStoppedRedisContainerToDependencyError() {
        GenericContainer<?> isolated = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
        isolated.start();
        LettuceConnectionFactory isolatedFactory = connectionFactory(isolated);
        try {
            ReactiveStringRedisTemplate isolatedRedis = new ReactiveStringRedisTemplate(isolatedFactory);
            RedisSessionVerifier isolatedVerifier = new RedisSessionVerifier(
                    isolatedRedis, new GatewayRuntimeProperties(environment));
            isolated.stop();

            assertThat(isolatedVerifier.verify(SUBJECT, "session-stopped", TOKEN_VERSION)
                    .block(Duration.ofSeconds(5))).isEqualTo(SessionCheckResult.DEPENDENCY_ERROR);
        } finally {
            isolatedFactory.destroy();
            if (isolated.isRunning()) {
                isolated.stop();
            }
        }
    }

    private SessionCheckResult verify(String sessionId) {
        return verifier.verify(SUBJECT, sessionId, TOKEN_VERSION).block(BLOCK_TIMEOUT);
    }

    private void writeSession(
            String sessionId, String subject, String status, long version, Duration ttl) {
        String key = key(sessionId);
        redis.opsForHash().putAll(key, Map.of(
                "subject", subject,
                "status", status,
                "tokenVersion", Long.toString(version))).block(BLOCK_TIMEOUT);
        assertThat(redis.expire(key, ttl).block(BLOCK_TIMEOUT)).isTrue();
    }

    private List<String> ownedKeys() {
        return redis.scan(ScanOptions.scanOptions().match(key("*")).count(100).build())
                .collectList()
                .block(BLOCK_TIMEOUT);
    }

    private String key(String sessionId) {
        return "educloud:{" + environment + ":auth}:session:" + sessionId;
    }

    private static LettuceConnectionFactory connectionFactory(GenericContainer<?> container) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                container.getHost(), container.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static String environment() {
        return "it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
