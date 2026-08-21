package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayRateLimitProperties;
import com.educloud.gateway.config.GatewayRuntimeProperties;
import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.ratelimit.BucketRequest;
import com.educloud.gateway.ratelimit.BucketRule;
import com.educloud.gateway.ratelimit.GatewayRateLimitWebFilter;
import com.educloud.gateway.ratelimit.HmacKeyHasher;
import com.educloud.gateway.ratelimit.LoginNameExtractor;
import com.educloud.gateway.ratelimit.RateLimitDecision;
import com.educloud.gateway.ratelimit.RedisTokenBucketLimiter;
import com.educloud.gateway.route.AccessPolicy;
import com.educloud.gateway.route.InternalPathWebFilter;
import com.educloud.gateway.security.IdentityHeaderWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayFilterOrderTest {

    @Test
    void locksTheEdgeOrderAheadOfSpringSecurityAndRouting() {
        GatewayWebProperties web = GatewayCorsTest.properties();
        GatewayErrorWriter writer = writer();
        CorsWebFilter cors = new CorsConfiguration().gatewayCorsWebFilter(web);

        assertThat(new RequestIdWebFilter().getOrder()).isEqualTo(-300);
        assertThat(new SecurityHeadersWebFilter(new GatewayRuntimeProperties("local")).getOrder()).isEqualTo(-290);
        assertThat(new InternalPathWebFilter(AccessPolicy.standard(), writer).getOrder()).isEqualTo(-280);
        assertThat(new RequestBodyCachingWebFilter(AccessPolicy.standard(), web, writer).getOrder()).isEqualTo(-270);
        assertThat(new IdentityHeaderWebFilter(new ClientIpResolver(web), writer).getOrder()).isEqualTo(-260);
        assertThat(new OriginPolicyWebFilter(web, writer).getOrder()).isEqualTo(-255);
        assertThat(cors).isInstanceOf(Ordered.class);
        assertThat(((Ordered) cors).getOrder()).isEqualTo(-254);
        assertThat(GatewayFilterOrders.RATE_LIMIT).isEqualTo(-250);
        assertThat(GatewayFilterOrders.RATE_LIMIT).isLessThan(0);
    }

    @Test
    void shortCircuitsBeforeDependenciesAndKeepsTheReplayedBodyRequestScoped() {
        GatewayWebProperties web = GatewayCorsTest.properties();
        GatewayErrorWriter writer = writer();
        AccessPolicy accessPolicy = AccessPolicy.standard();
        RedisTokenBucketLimiter limiter = mock(RedisTokenBucketLimiter.class);
        BucketRequest bucket = new BucketRequest(
                "educloud:{local:ratelimit}:ordinary:" + "a".repeat(64),
                new BucketRule(20, Duration.ofSeconds(1), 40));
        when(limiter.bucket(anyString(), anyString(), any())).thenReturn(bucket);
        when(limiter.acquire(anyList())).thenReturn(
                Mono.just(new RateLimitDecision(true, Duration.ZERO)));
        GatewayRateLimitProperties rateProperties = rateProperties();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("gatewayMetrics", GatewayMetrics.noOp());
        GatewayRateLimitWebFilter rateLimit = new GatewayRateLimitWebFilter(
                accessPolicy,
                limiter,
                new HmacKeyHasher(rateProperties),
                new LoginNameExtractor(GatewayCorsTest.objectMapper()),
                rateProperties,
                writer,
                beans.getBeanProvider(GatewayMetrics.class));
        AtomicInteger identityCalls = new AtomicInteger();
        WebFilter identity = new OrderedFilter(GatewayFilterOrders.CLIENT_IDENTITY, (exchange, chain) -> {
            identityCalls.incrementAndGet();
            exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_IP, "127.0.0.1");
            return chain.filter(exchange);
        });
        AtomicInteger jwtCalls = new AtomicInteger();
        AtomicReference<byte[]> replayedBody = new AtomicReference<>();
        WebFilter jwt = new OrderedFilter(-100, (exchange, chain) -> {
            jwtCalls.incrementAndGet();
            assertThat(GatewayExchangeAttributes.cachedRequestBody(exchange)).isEmpty();
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .doOnNext(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        replayedBody.set(bytes);
                    })
                    .then(chain.filter(exchange));
        });
        WebTestClient client = WebTestClient.bindToWebHandler(exchange -> {
                    exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                    return exchange.getResponse().setComplete();
                })
                .webFilter(
                        new RequestIdWebFilter(),
                        new SecurityHeadersWebFilter(new GatewayRuntimeProperties("local")),
                        new InternalPathWebFilter(accessPolicy, writer),
                        new RequestBodyCachingWebFilter(accessPolicy, web, writer),
                        identity,
                        new OriginPolicyWebFilter(web, writer),
                        new CorsConfiguration().gatewayCorsWebFilter(web),
                        rateLimit,
                        jwt)
                .build();

        client.get().uri("/internal/v1/admin").exchange().expectStatus().isNotFound();
        assertThat(identityCalls).hasValue(0);
        verify(limiter, never()).acquire(anyList());
        assertThat(jwtCalls).hasValue(0);

        client.get().uri("/api/v1/courses")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173.evil.example")
                .exchange().expectStatus().isForbidden();
        assertThat(identityCalls).hasValue(1);
        verify(limiter, never()).acquire(anyList());
        assertThat(jwtCalls).hasValue(0);

        byte[] body = "{\"loginName\":\"Alice\",\"password\":\"secret\"}"
                .getBytes(StandardCharsets.UTF_8);
        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange().expectStatus().isNoContent();
        verify(limiter).acquire(anyList());
        assertThat(jwtCalls).hasValue(1);
        assertThat(replayedBody.get()).containsExactly(body);
    }

    private static GatewayRateLimitProperties rateProperties() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setHmacSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }

    private static GatewayErrorWriter writer() {
        return new GatewayErrorWriter(
                GatewayCorsTest.objectMapper(),
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));
    }

    private record OrderedFilter(int order, WebFilter delegate) implements WebFilter, Ordered {
        @Override
        public Mono<Void> filter(
                org.springframework.web.server.ServerWebExchange exchange,
                WebFilterChain chain) {
            return delegate.filter(exchange, chain);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
