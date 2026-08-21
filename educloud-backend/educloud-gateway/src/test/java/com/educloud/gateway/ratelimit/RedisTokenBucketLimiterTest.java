package com.educloud.gateway.ratelimit;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class RedisTokenBucketLimiterTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    void executesAllBucketsAtomicallyWithOneSharedHashTag() {
        ReactiveStringRedisTemplate redis = redisReturning(List.of(1L, 0L));
        RedisTokenBucketLimiter limiter = limiter(redis);
        List<BucketRequest> buckets = List.of(
                limiter.bucket("ordinary", DIGEST_A,
                        new BucketRule(20, Duration.ofSeconds(1), 40)),
                limiter.bucket("login-account", DIGEST_B,
                        new BucketRule(5, Duration.ofMinutes(5), 5)));

        assertThat(limiter.acquire(buckets).block())
                .isEqualTo(new RateLimitDecision(true, Duration.ZERO));

        verify(redis).execute(any(RedisScript.class), org.mockito.ArgumentMatchers.eq(List.of(
                "educloud:{test-env:ratelimit}:ordinary:" + DIGEST_A,
                "educloud:{test-env:ratelimit}:login-account:" + DIGEST_B)),
                org.mockito.ArgumentMatchers.eq(List.of("20", "1000", "40", "5", "300000", "5")));
    }

    @Test
    void mapsDeniedProtocolToThePositiveMaximumRetryDelay() {
        ReactiveStringRedisTemplate redis = redisReturning(List.of(0L, 1501L));
        RedisTokenBucketLimiter limiter = limiter(redis);

        RateLimitDecision decision = limiter.acquire(List.of(limiter.bucket(
                "ordinary", DIGEST_A, new BucketRule(20, Duration.ofSeconds(1), 40)))).block();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofMillis(1501));
    }

    @Test
    void failsClosedOnEmptyMalformedOrRedisErrorResults() {
        List<Flux<List>> results = List.of(
                Flux.empty(),
                Flux.just((List) List.of()),
                Flux.just((List) List.of(1L)),
                Flux.just((List) List.of(2L, 0L)),
                Flux.just((List) List.of(0L, 0L)),
                Flux.<List>error(new IllegalStateException("redis detail")));
        for (Flux<List> result : results) {
            ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
            when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(result);
            RedisTokenBucketLimiter limiter = limiter(redis);
                    assertThatThrownBy(() -> limiter.acquire(List.of(limiter.bucket(
                            "ordinary", DIGEST_A,
                            new BucketRule(20, Duration.ofSeconds(1), 40)))).block())
                    .isInstanceOf(RedisTokenBucketLimiter.RateLimitDependencyException.class)
                    .hasMessageNotContaining("redis detail");
        }
    }

    @Test
    void luaUsesRedisTimeTwoPhaseUpdatesAndBoundedExpiryWithoutKeysCommand() throws Exception {
        String script = new ClassPathResource("com/educloud/gateway/ratelimit/token-bucket.lua")
                .getContentAsString(StandardCharsets.UTF_8)
                .toUpperCase(Locale.ROOT);

        assertThat(script).contains(
                "REDIS.CALL('TIME')", "EXISTS", "HMGET", "PTTL", "HSET", "PEXPIRE",
                "CORRUPT TOKEN BUCKET STATE");
        assertThat(Pattern.compile("REDIS\\.CALL\\(['\"]KEYS['\"]").matcher(script).find()).isFalse();
        assertThat(script).doesNotContain("SYSTEM.CURRENTTIMEMILLIS", "INSTANT.NOW");
        assertThat(script.indexOf("RETURN {0")).isLessThan(script.indexOf("HSET"));
    }

    @Test
    void rejectsProgrammaticRulesOutsideTheValidatedSafetyBounds() {
        assertThatThrownBy(() -> new BucketRule(
                1_000_001, Duration.ofSeconds(1), 1_000_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safety bounds");
        assertThatThrownBy(() -> new BucketRule(
                1, Duration.ofSeconds(1), 1_000_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safety bounds");
        assertThatThrownBy(() -> new BucketRule(
                1, Duration.ofNanos(1), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safety bounds");
        assertThatThrownBy(() -> new BucketRule(
                1, Duration.ofDays(1).plusMillis(1), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safety bounds");
        assertThatThrownBy(() -> new BucketRule(
                1, Duration.ofHours(24), 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safety bounds");
    }

    private static ReactiveStringRedisTemplate redisReturning(List<?> result) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(result));
        return redis;
    }

    private static RedisTokenBucketLimiter limiter(ReactiveStringRedisTemplate redis) {
        return new RedisTokenBucketLimiter(redis, new GatewayRuntimeProperties("test-env"), Duration.ofSeconds(1));
    }
}
