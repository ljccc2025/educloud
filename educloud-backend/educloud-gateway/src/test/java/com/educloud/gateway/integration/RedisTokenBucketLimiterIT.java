package com.educloud.gateway.integration;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import com.educloud.gateway.ratelimit.BucketRequest;
import com.educloud.gateway.ratelimit.BucketRule;
import com.educloud.gateway.ratelimit.RateLimitDecision;
import com.educloud.gateway.ratelimit.RedisTokenBucketLimiter;
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
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisTokenBucketLimiterIT {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.2.5-alpine");
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    private final String environment = environment();
    private GenericContainer<?> redisContainer;
    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redis;
    private RedisTokenBucketLimiter limiter;

    @BeforeAll
    void startRedis() {
        redisContainer = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
        redisContainer.start();
        connectionFactory = connectionFactory(redisContainer);
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        limiter = new RedisTokenBucketLimiter(redis, new GatewayRuntimeProperties(environment));
    }

    @AfterEach
    void removeOwnedKeys() {
        List<String> keys = ownedKeys();
        if (!keys.isEmpty()) {
            redis.delete(Flux.fromIterable(keys)).block(BLOCK_TIMEOUT);
        }
        assertThat(ownedKeys()).as("owned Redis rate-limit keys after cleanup").isEmpty();
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
    void concurrentRequestsNeverExceedBurstAndDenialsHaveRetryAfter() {
        BucketRequest bucket = limiter.bucket(
                "ordinary", DIGEST_A, new BucketRule(5, Duration.ofMinutes(1), 10));

        List<RateLimitDecision> decisions = Flux.range(0, 200)
                .flatMap(index -> limiter.acquire(List.of(bucket)), 32)
                .collectList()
                .block(Duration.ofSeconds(20));

        assertThat(decisions).isNotNull().hasSize(200);
        assertThat(decisions.stream().filter(RateLimitDecision::allowed).count()).isEqualTo(10);
        assertThat(decisions.stream().filter(decision -> !decision.allowed()))
                .allMatch(decision -> !decision.retryAfter().isZero()
                        && !decision.retryAfter().isNegative());
    }

    @Test
    void aDeniedMultiBucketRequestDoesNotPartiallyDeductAnotherBucket() {
        BucketRequest ordinary = limiter.bucket(
                "ordinary", DIGEST_A, new BucketRule(10, Duration.ofMinutes(1), 10));
        BucketRequest account = limiter.bucket(
                "login-account", DIGEST_B, new BucketRule(1, Duration.ofMinutes(5), 1));
        assertThat(limiter.acquire(List.of(account)).block(BLOCK_TIMEOUT).allowed()).isTrue();

        String before = hashField(ordinary.key(), "tokens");
        RateLimitDecision denied = limiter.acquire(List.of(ordinary, account)).block(BLOCK_TIMEOUT);
        String after = hashField(ordinary.key(), "tokens");

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isPositive();
        assertThat(before).isNull();
        assertThat(after).isNull();
    }

    @Test
    void usesRedisTimeForRefillAndAppliesBoundedKeyTtl() {
        BucketRequest refill = limiter.bucket(
                "ordinary", DIGEST_A, new BucketRule(2, Duration.ofSeconds(1), 2));
        assertThat(limiter.acquire(List.of(refill)).block(BLOCK_TIMEOUT).allowed()).isTrue();
        assertThat(limiter.acquire(List.of(refill)).block(BLOCK_TIMEOUT).allowed()).isTrue();
        assertThat(limiter.acquire(List.of(refill)).block(BLOCK_TIMEOUT).allowed()).isFalse();

        Duration ttl = redis.getExpire(refill.key()).block(BLOCK_TIMEOUT);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(1));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(
                limiter.acquire(List.of(refill)).block(BLOCK_TIMEOUT).allowed()).isTrue());

        BucketRequest expiring = limiter.bucket(
                "login-ip", DIGEST_B, new BucketRule(1, Duration.ofMillis(100), 1));
        assertThat(limiter.acquire(List.of(expiring)).block(BLOCK_TIMEOUT).allowed()).isTrue();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(
                redis.hasKey(expiring.key()).block(BLOCK_TIMEOUT)).isFalse());
    }

    @Test
    void failsClosedForMalformedOrPersistentBucketState() {
        BucketRequest malformed = limiter.bucket(
                "ordinary", DIGEST_A, new BucketRule(2, Duration.ofSeconds(1), 2));
        redis.opsForHash().putAll(malformed.key(), Map.of(
                "tokens", "not-a-number",
                "timestamp", "1")).block(BLOCK_TIMEOUT);
        redis.expire(malformed.key(), Duration.ofSeconds(10)).block(BLOCK_TIMEOUT);

        assertThatThrownBy(() -> limiter.acquire(List.of(malformed)).block(BLOCK_TIMEOUT))
                .isInstanceOf(RedisTokenBucketLimiter.RateLimitDependencyException.class);

        BucketRequest persistent = limiter.bucket(
                "login-ip", DIGEST_B, new BucketRule(1, Duration.ofSeconds(1), 1));
        redis.opsForHash().putAll(persistent.key(), Map.of(
                "tokens", "1",
                "timestamp", "1")).block(BLOCK_TIMEOUT);

        assertThatThrownBy(() -> limiter.acquire(List.of(persistent)).block(BLOCK_TIMEOUT))
                .isInstanceOf(RedisTokenBucketLimiter.RateLimitDependencyException.class);
    }

    @Test
    void failsClosedWhenRedisStops() {
        GenericContainer<?> isolated = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
        isolated.start();
        LettuceConnectionFactory isolatedFactory = connectionFactory(isolated);
        try {
            ReactiveStringRedisTemplate isolatedRedis = new ReactiveStringRedisTemplate(isolatedFactory);
            RedisTokenBucketLimiter isolatedLimiter = new RedisTokenBucketLimiter(
                    isolatedRedis, new GatewayRuntimeProperties(environment));
            BucketRequest bucket = isolatedLimiter.bucket(
                    "ordinary", DIGEST_A, new BucketRule(1, Duration.ofSeconds(1), 1));
            isolated.stop();

            assertThatThrownBy(() -> isolatedLimiter.acquire(List.of(bucket))
                    .block(Duration.ofSeconds(5)))
                    .isInstanceOf(RedisTokenBucketLimiter.RateLimitDependencyException.class);
        } finally {
            isolatedFactory.destroy();
            if (isolated.isRunning()) {
                isolated.stop();
            }
        }
    }

    private String hashField(String key, String field) {
        Object value = redis.opsForHash().get(key, field).block(BLOCK_TIMEOUT);
        return value == null ? null : value.toString();
    }

    private List<String> ownedKeys() {
        return redis.scan(ScanOptions.scanOptions()
                        .match("educloud:{" + environment + ":ratelimit}:*")
                        .count(100)
                        .build())
                .collectList()
                .block(BLOCK_TIMEOUT);
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
