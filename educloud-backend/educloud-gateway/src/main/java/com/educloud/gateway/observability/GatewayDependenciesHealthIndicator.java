package com.educloud.gateway.observability;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.educloud.gateway.security.JwksState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Objects;

@Component("gatewayDependencies")
public final class GatewayDependenciesHealthIndicator implements ReactiveHealthIndicator {

    private final JwksState jwksState;
    private final ReactiveRedisConnectionFactory redis;
    private final NacosServiceManager nacos;
    private final Duration timeout;
    private final GatewayMetrics metrics;

    @Autowired
    public GatewayDependenciesHealthIndicator(
            JwksState jwksState,
            ReactiveRedisConnectionFactory redis,
            NacosServiceManager nacos,
            @Value("${educloud.gateway.health.dependency-timeout:2s}") Duration timeout,
            ObjectProvider<GatewayMetrics> metricsProvider) {
        this(jwksState, redis, nacos, timeout,
                metricsProvider.getIfAvailable(GatewayMetrics::noOp));
    }

    GatewayDependenciesHealthIndicator(
            JwksState jwksState,
            ReactiveRedisConnectionFactory redis,
            NacosServiceManager nacos,
            Duration timeout,
            GatewayMetrics metrics) {
        this.jwksState = Objects.requireNonNull(jwksState, "jwksState");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.nacos = Objects.requireNonNull(nacos, "nacos");
        this.timeout = requirePositive(timeout);
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public Mono<Health> health() {
        Mono<Boolean> jwksReady = Mono.fromSupplier(jwksState::loaded);
        Mono<Boolean> redisReady = redisReady();
        Mono<Boolean> nacosReady = nacosReady();
        return Mono.zip(jwksReady, redisReady, nacosReady)
                .map(states -> health(states.getT1(), states.getT2(), states.getT3()));
    }

    private Mono<Boolean> redisReady() {
        return Mono.usingWhen(
                        Mono.fromSupplier(redis::getReactiveConnection),
                        connection -> connection.ping().map("PONG"::equalsIgnoreCase),
                        ReactiveRedisConnection::closeLater)
                .timeout(timeout)
                .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> nacosReady() {
        return Mono.fromCallable(() -> "UP".equalsIgnoreCase(
                        nacos.getNamingService().getServerStatus()))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(timeout)
                .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    private Health health(boolean jwks, boolean redisReady, boolean nacosReady) {
        record(GatewayMetrics.Dependency.JWKS, jwks);
        record(GatewayMetrics.Dependency.REDIS, redisReady);
        record(GatewayMetrics.Dependency.NACOS, nacosReady);
        Health.Builder builder = jwks && redisReady && nacosReady ? Health.up() : Health.down();
        return builder
                .withDetail("jwks", state(jwks))
                .withDetail("redis", state(redisReady))
                .withDetail("nacos", state(nacosReady))
                .build();
    }

    private void record(GatewayMetrics.Dependency dependency, boolean up) {
        metrics.recordDependency(dependency,
                up ? GatewayMetrics.DependencyResult.UP : GatewayMetrics.DependencyResult.DOWN);
    }

    private static String state(boolean up) {
        return up ? "UP" : "DOWN";
    }

    private static Duration requirePositive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("dependency health timeout must be positive");
        }
        return value;
    }
}
