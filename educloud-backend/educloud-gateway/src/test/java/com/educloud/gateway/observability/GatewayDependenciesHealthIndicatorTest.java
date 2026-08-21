package com.educloud.gateway.observability;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;
import com.educloud.gateway.security.JwksState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayDependenciesHealthIndicatorTest {

    @Test
    void reportsUpOnlyWhenJwksRedisAndNacosAreReady() {
        Fixture fixture = fixture(true, Mono.just("PONG"), "UP");

        Health health = fixture.indicator().health().block();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsOnly(
                org.assertj.core.data.MapEntry.entry("jwks", "UP"),
                org.assertj.core.data.MapEntry.entry("redis", "UP"),
                org.assertj.core.data.MapEntry.entry("nacos", "UP"));
    }

    @Test
    void dependencyFailuresAndTimeoutsOnlyMakeThisReadinessContributorDown() {
        for (Fixture fixture : new Fixture[]{
                fixture(false, Mono.just("PONG"), "UP"),
                fixture(true, Mono.error(new IllegalStateException("redis address")), "UP"),
                fixture(true, Mono.just("NOPE"), "UP"),
                fixture(true, Mono.just("PONG"), "DOWN"),
                fixture(true, Mono.never(), "UP")}) {
            Health health = fixture.indicator().health().block(Duration.ofSeconds(1));

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsOnlyKeys("jwks", "redis", "nacos");
            assertThat(health.getDetails().values()).allMatch(value ->
                    "UP".equals(value) || "DOWN".equals(value));
        }
    }

    private static Fixture fixture(boolean jwksLoaded, Mono<String> redisPing, String nacosStatus) {
        JwksState jwks = mock(JwksState.class);
        when(jwks.loaded()).thenReturn(jwksLoaded);
        ReactiveRedisConnection connection = mock(ReactiveRedisConnection.class);
        when(connection.ping()).thenReturn(redisPing);
        when(connection.closeLater()).thenReturn(Mono.empty());
        ReactiveRedisConnectionFactory redis = mock(ReactiveRedisConnectionFactory.class);
        when(redis.getReactiveConnection()).thenReturn(connection);
        NamingService naming = mock(NamingService.class);
        when(naming.getServerStatus()).thenReturn(nacosStatus);
        NacosServiceManager nacos = mock(NacosServiceManager.class);
        when(nacos.getNamingService()).thenReturn(naming);
        return new Fixture(new GatewayDependenciesHealthIndicator(
                jwks, redis, nacos, Duration.ofMillis(50), GatewayMetrics.noOp()));
    }

    private record Fixture(GatewayDependenciesHealthIndicator indicator) {
    }
}
