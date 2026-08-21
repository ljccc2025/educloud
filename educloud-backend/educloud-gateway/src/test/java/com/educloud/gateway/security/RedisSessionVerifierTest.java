package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class RedisSessionVerifierTest {

    private static final String SUBJECT = "user-123";
    private static final String SESSION_ID = "session:abc-123";
    private static final long TOKEN_VERSION = 7L;

    @Test
    void springSelectsTheRuntimeDependencyConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ReactiveStringRedisTemplate.class,
                    () -> mock(ReactiveStringRedisTemplate.class));
            context.registerBean(GatewayRuntimeProperties.class,
                    () -> new GatewayRuntimeProperties("test-env"));
            context.register(RedisSessionVerifier.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(RedisSessionVerifier.class)).isNotNull();
        }
    }

    @Test
    void classifiesEveryStableRedisProtocolResult() {
        assertResult(List.of(1L, SUBJECT, "ACTIVE", "7", 30_000L), SessionCheckResult.ACTIVE);
        assertResult(List.of(0L), SessionCheckResult.MISSING);
        assertResult(List.of(1L, SUBJECT, "REVOKED", "7", 30_000L), SessionCheckResult.REVOKED);
        assertResult(List.of(1L, "user-456", "ACTIVE", "7", 30_000L),
                SessionCheckResult.SUBJECT_MISMATCH);
        assertResult(List.of(1L, SUBJECT, "ACTIVE", "8", 30_000L),
                SessionCheckResult.VERSION_MISMATCH);
    }

    @Test
    void acceptsTheBulkValueTypesReturnedByReactiveRedis() {
        assertResult(List.of(
                        bytes("1"), bytes(SUBJECT), bytes("ACTIVE"), bytes("7"), 30_000L),
                SessionCheckResult.ACTIVE);
    }

    @Test
    void mapsMalformedProtocolShapesAndValuesToCorrupt() {
        List<List<?>> corruptResults = List.of(
                List.of(),
                List.of(0L, "unexpected"),
                List.of(2L),
                List.of(1L, SUBJECT, "ACTIVE", "7"),
                List.of(1L, "", "ACTIVE", "7", 30_000L),
                List.of(1L, "unsafe{subject", "ACTIVE", "7", 30_000L),
                List.of(1L, SUBJECT, "", "7", 30_000L),
                List.of(1L, SUBJECT, "UNKNOWN", "7", 30_000L),
                List.of(1L, SUBJECT, "ACTIVE", "not-a-number", 30_000L),
                List.of(1L, SUBJECT, "ACTIVE", "-1", 30_000L),
                List.of(1L, SUBJECT, "ACTIVE", "7", 0L),
                List.of(1L, SUBJECT, "ACTIVE", "7", -1L),
                List.of("not-a-flag"));

        corruptResults.forEach(result -> assertResult(result, SessionCheckResult.CORRUPT));
        assertEmptyResult(SessionCheckResult.CORRUPT);
    }

    @Test
    void mapsRedisTimeoutConnectionAndScriptErrorsToDependencyError() {
        assertError(new QueryTimeoutException("redis timeout"));
        assertError(new RedisConnectionFailureException("redis unavailable"));
        assertError(new IllegalStateException("script failed"));
        assertSynchronousError(new IllegalStateException("client failed before returning a publisher"));

        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.never());
        RedisSessionVerifier verifier = verifier(redis, Duration.ofMillis(10));
        assertThat(verifier.verify(SUBJECT, SESSION_ID, TOKEN_VERSION).block(Duration.ofSeconds(1)))
                .isEqualTo(SessionCheckResult.DEPENDENCY_ERROR);
    }

    @Test
    void buildsOnlyTheNamespacedSessionKeyAndPassesNoScriptArguments() {
        ReactiveStringRedisTemplate redis = mockRedis(List.of(1L, SUBJECT, "ACTIVE", "7", 30_000L));
        RedisSessionVerifier verifier = verifier(redis, Duration.ofSeconds(1));

        assertThat(verifier.verify(SUBJECT, SESSION_ID, TOKEN_VERSION).block())
                .isEqualTo(SessionCheckResult.ACTIVE);

        verify(redis).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of(
                        "educloud:{test-env:auth}:session:session:abc-123")),
                org.mockito.ArgumentMatchers.eq(List.of()));
    }

    @Test
    void rejectsUnsafeInputsWithoutCallingRedis() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        RedisSessionVerifier verifier = verifier(redis, Duration.ofSeconds(1));

        assertThat(verifier.verify("", SESSION_ID, TOKEN_VERSION).block())
                .isEqualTo(SessionCheckResult.CORRUPT);
        assertThat(verifier.verify(SUBJECT, "unsafe{sid", TOKEN_VERSION).block())
                .isEqualTo(SessionCheckResult.CORRUPT);
        assertThat(verifier.verify(SUBJECT, SESSION_ID, -1).block())
                .isEqualTo(SessionCheckResult.CORRUPT);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyList());
    }

    @Test
    void refusesAnUnvalidatedEnvironmentEvenWhenConstructedDirectly() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);

        assertThatThrownBy(() -> new RedisSessionVerifier(
                redis, new GatewayRuntimeProperties("unsafe{env"), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment");
    }

    private static void assertResult(List<?> redisResult, SessionCheckResult expected) {
        ReactiveStringRedisTemplate redis = mockRedis(redisResult);
        assertThat(verifier(redis, Duration.ofSeconds(1))
                .verify(SUBJECT, SESSION_ID, TOKEN_VERSION)
                .block()).isEqualTo(expected);
    }

    private static void assertEmptyResult(SessionCheckResult expected) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.empty());
        assertThat(verifier(redis, Duration.ofSeconds(1))
                .verify(SUBJECT, SESSION_ID, TOKEN_VERSION)
                .block()).isEqualTo(expected);
    }

    private static void assertError(RuntimeException error) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.error(error));
        assertThat(verifier(redis, Duration.ofSeconds(1))
                .verify(SUBJECT, SESSION_ID, TOKEN_VERSION)
                .block()).isEqualTo(SessionCheckResult.DEPENDENCY_ERROR);
    }

    private static void assertSynchronousError(RuntimeException error) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenThrow(error);
        assertThat(verifier(redis, Duration.ofSeconds(1))
                .verify(SUBJECT, SESSION_ID, TOKEN_VERSION)
                .block()).isEqualTo(SessionCheckResult.DEPENDENCY_ERROR);
    }

    private static ReactiveStringRedisTemplate mockRedis(List<?> result) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(result));
        return redis;
    }

    private static RedisSessionVerifier verifier(ReactiveStringRedisTemplate redis, Duration timeout) {
        return new RedisSessionVerifier(redis, new GatewayRuntimeProperties("test-env"), timeout);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
