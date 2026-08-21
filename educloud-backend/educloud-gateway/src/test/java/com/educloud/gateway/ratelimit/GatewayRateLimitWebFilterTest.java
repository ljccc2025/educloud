package com.educloud.gateway.ratelimit;

import com.educloud.gateway.config.GatewayRateLimitProperties;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.route.AccessPolicy;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.educloud.gateway.web.GatewayFilterOrders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayRateLimitWebFilterTest {

    @Test
    void buildsOrdinaryLoginAndPaymentBucketsAndContinuesWhenAllowed() {
        assertAllowedBuckets("/api/v1/users/me", null, null, 1);
        assertAllowedBuckets("/api/v1/auth/login", MediaType.APPLICATION_JSON,
                "{\"loginName\":\"  ＡLICE  \",\"password\":\"secret\"}", 3);
        assertAllowedBuckets("/api/v1/payment-callbacks/alipay/notify", null, null, 2);
    }

    @Test
    void deniedDecisionWrites429WithTheLimiterRetryDelay() {
        RedisTokenBucketLimiter limiter = mock(RedisTokenBucketLimiter.class);
        stubBuckets(limiter);
        when(limiter.acquire(anyList())).thenReturn(Mono.just(
                new RateLimitDecision(false, Duration.ofMillis(1501))));
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);

        filter(limiter, writer, mock(GatewayMetrics.class))
                .filter(exchange("/api/v1/users/me", null, null, null), chain).block();

        verify(writer).write(any(), org.mockito.ArgumentMatchers.argThat(
                (GatewayFailure failure) -> failure.code() == GatewayErrorCode.RATE_LIMITED
                        && failure.retryAfter().orElseThrow().equals(Duration.ofMillis(1501))));
        verify(chain, never()).filter(any());
    }

    @Test
    void onlyAnonymousPublicReadsFailOpenWhenRedisIsUnavailable() {
        for (FailureCase failureCase : List.of(
                new FailureCase("/api/v1/courses", null, true),
                new FailureCase("/api/v1/courses", "Bearer token", false),
                new FailureCase("/api/v1/users/me", null, false),
                new FailureCase("/api/v1/auth/register", null, false),
                new FailureCase("/api/v1/payment-callbacks/alipay/notify", null, false))) {
            RedisTokenBucketLimiter limiter = mock(RedisTokenBucketLimiter.class);
            stubBuckets(limiter);
            when(limiter.acquire(anyList())).thenReturn(Mono.error(new IllegalStateException("redis detail")));
            GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
            when(writer.write(any(), any())).thenReturn(Mono.empty());
            GatewayMetrics metrics = mock(GatewayMetrics.class);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            filter(limiter, writer, metrics).filter(
                    exchange(failureCase.path(), null, null, failureCase.authorization()), chain).block();

            if (failureCase.failOpen()) {
                verify(chain).filter(any());
                verify(metrics).recordRateLimitDegraded("catalog");
                verify(writer, never()).write(any(), any());
            } else {
                verify(writer).write(any(), org.mockito.ArgumentMatchers.argThat(
                        (GatewayFailure failure) -> failure.code() == GatewayErrorCode.DEPENDENCY_UNAVAILABLE));
                verify(chain, never()).filter(any());
            }
        }
    }

    @Test
    void loginRequiresExactJsonAndASafelyParsedCachedLoginName() {
        RedisTokenBucketLimiter limiter = mock(RedisTokenBucketLimiter.class);
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);

        filter(limiter, writer, mock(GatewayMetrics.class)).filter(
                exchange("/api/v1/auth/login", MediaType.TEXT_PLAIN, "loginName=alice", null), chain).block();
        verify(writer).write(any(), org.mockito.ArgumentMatchers.argThat(
                (GatewayFailure failure) -> failure.code() == GatewayErrorCode.GATEWAY_UNSUPPORTED_MEDIA_TYPE));
        verify(limiter, never()).acquire(anyList());

        GatewayErrorWriter badJsonWriter = mock(GatewayErrorWriter.class);
        when(badJsonWriter.write(any(), any())).thenReturn(Mono.empty());
        filter(limiter, badJsonWriter, mock(GatewayMetrics.class)).filter(
                exchange("/api/v1/auth/login", MediaType.APPLICATION_JSON, "{\"password\":\"secret\"}", null),
                chain).block();
        verify(badJsonWriter).write(any(), org.mockito.ArgumentMatchers.argThat(
                (GatewayFailure failure) -> failure.code() == GatewayErrorCode.GATEWAY_BAD_REQUEST));
    }

    @Test
    void runsAtTheFixedRateLimitOrder() {
        assertThat(filter(mock(RedisTokenBucketLimiter.class), mock(GatewayErrorWriter.class),
                mock(GatewayMetrics.class)).getOrder()).isEqualTo(GatewayFilterOrders.RATE_LIMIT);
    }

    private static void assertAllowedBuckets(
            String path, MediaType contentType, String body, int expectedBuckets) {
        RedisTokenBucketLimiter limiter = mock(RedisTokenBucketLimiter.class);
        stubBuckets(limiter);
        when(limiter.acquire(anyList())).thenReturn(Mono.just(new RateLimitDecision(true, Duration.ZERO)));
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter(limiter, writer, mock(GatewayMetrics.class))
                .filter(exchange(path, contentType, body, null), chain).block();

        verify(limiter).acquire(org.mockito.ArgumentMatchers.argThat(
                buckets -> buckets.size() == expectedBuckets
                        && buckets.stream().allMatch(bucket -> !bucket.key().contains("198.51.100.25"))
                        && buckets.stream().allMatch(bucket -> !bucket.key().contains("alice"))));
        verify(chain).filter(any());
        verify(writer, never()).write(any(), any());
    }

    private static GatewayRateLimitWebFilter filter(
            RedisTokenBucketLimiter limiter, GatewayErrorWriter writer, GatewayMetrics metrics) {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        properties.setHmacSecretBase64(Base64.getEncoder().encodeToString(secret));
        return new GatewayRateLimitWebFilter(
                AccessPolicy.standard(),
                limiter,
                new HmacKeyHasher(properties),
                new LoginNameExtractor(new ObjectMapper()),
                properties,
                writer,
                metrics);
    }

    private static MockServerWebExchange exchange(
            String path, MediaType contentType, String body, String authorization) {
        HttpMethod method = path.equals("/api/v1/courses") || path.equals("/api/v1/users/me")
                ? HttpMethod.GET : HttpMethod.POST;
        MockServerHttpRequest.BodyBuilder request = MockServerHttpRequest.method(method, path);
        if (contentType != null) {
            request.contentType(contentType);
        }
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(
                body == null ? request.build() : request.body(body));
        exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_IP, "198.51.100.25");
        if (body != null) {
            GatewayExchangeAttributes.cacheRequestBody(
                    exchange, body.getBytes(StandardCharsets.UTF_8));
        }
        return exchange;
    }

    private static void stubBuckets(RedisTokenBucketLimiter limiter) {
        when(limiter.bucket(any(), any(), any())).thenAnswer(invocation -> {
            String dimension = invocation.getArgument(0);
            String digest = invocation.getArgument(1);
            BucketRule rule = invocation.getArgument(2);
            return new BucketRequest("educloud:{test-env:ratelimit}:" + dimension + ":" + digest, rule);
        });
    }

    private record FailureCase(String path, String authorization, boolean failOpen) {
    }
}
