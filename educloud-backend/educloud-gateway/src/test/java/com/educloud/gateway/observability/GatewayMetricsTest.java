package com.educloud.gateway.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.educloud.gateway.config.GatewayRuntimeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class GatewayMetricsTest {

    @Test
    void publishesOnlyBoundedSecuritySessionRateLimitAndDependencyTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerGatewayMetrics metrics = new MicrometerGatewayMetrics(registry);

        metrics.recordSecurityFailure(
                GatewayMetrics.SecurityFailureCategory.AUTHENTICATION, "user-core");
        metrics.recordSessionCheck(GatewayMetrics.SessionResult.ACTIVE, "user-core");
        metrics.recordRateLimitDecision(
                GatewayMetrics.RateLimitResult.DENIED, "catalog");
        metrics.recordRateLimitDegraded("catalog");
        metrics.recordDependency(
                GatewayMetrics.Dependency.REDIS, GatewayMetrics.DependencyResult.DOWN);

        assertCounter(registry, "gateway.security.failures", "category", "authentication",
                "routeId", "user-core");
        assertCounter(registry, "gateway.session.checks", "result", "active",
                "routeId", "user-core");
        assertCounter(registry, "gateway.ratelimit.decisions", "result", "denied",
                "routeGroup", "catalog");
        assertCounter(registry, "gateway.ratelimit.degraded", "routeGroup", "catalog");
        assertCounter(registry, "gateway.dependencies", "dependency", "redis",
                "result", "down");

        metrics.recordSecurityFailure(
                GatewayMetrics.SecurityFailureCategory.AUTHORIZATION,
                "/api/v1/users/secret-user-id?sid=session-secret");
        metrics.recordRateLimitDegraded("198.51.100.22");
        assertCounter(registry, "gateway.security.failures", "category", "authorization",
                "routeId", "unmatched");
        assertCounter(registry, "gateway.ratelimit.degraded", "routeGroup", "unmatched");
        assertThat(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue()))
                .noneMatch(value -> value.contains("secret") || value.contains("198.51.100.22"));
    }

    @Test
    void observesBoundedRequestDimensionsWithoutUsingTheDynamicPath() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerGatewayMetrics metrics = new MicrometerGatewayMetrics(registry);
        GatewayObservationWebFilter filter = new GatewayObservationWebFilter(
                metrics, new GatewayRuntimeProperties("test"), "instance-a", (Tracer) null);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/secret-user-id"));
        exchange.getAttributes().put(
                GATEWAY_ROUTE_ATTR,
                Route.async().id("user-core").uri("http://downstream.example").predicate(e -> true).build());

        filter.filter(exchange, filtered -> {
            filtered.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return filtered.getResponse().setComplete();
        }).block();

        assertThat(registry.get("gateway.requests")
                .tag("environment", "test")
                .tag("routeId", "user-core")
                .tag("method", "GET")
                .tag("status", "204")
                .tag("category", "success")
                .timer().count()).isEqualTo(1);
        assertThat(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue()))
                .noneMatch(value -> value.contains("secret-user-id"));
    }

    @Test
    void sanitizesTheConfiguredInstanceBeforeStructuredRequestLogging() {
        Logger logger = (Logger) LoggerFactory.getLogger(GatewayObservationWebFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GatewayObservationWebFilter filter = new GatewayObservationWebFilter(
                    new MicrometerGatewayMetrics(new SimpleMeterRegistry()),
                    new GatewayRuntimeProperties("test"),
                    "instance-a\r\npassword=secret",
                    (Tracer) null);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/courses"));

            filter.filter(exchange, filtered -> {
                filtered.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                return filtered.getResponse().setComplete();
            }).block();

            assertThat(appender.list).singleElement().satisfies(event -> assertThat(
                    event.getFormattedMessage())
                    .contains("instance=unknown")
                    .doesNotContain("password=secret", "\r", "\n"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void samplesRepeatedDependencyWarningsWithoutDynamicOrSensitiveValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(MicrometerGatewayMetrics.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MicrometerGatewayMetrics metrics = new MicrometerGatewayMetrics(
                    new SimpleMeterRegistry());
            for (int index = 0; index < 100; index++) {
                metrics.recordRateLimitDecision(
                        GatewayMetrics.RateLimitResult.DEPENDENCY_ERROR,
                        "catalog");
            }

            assertThat(appender.list).singleElement().satisfies(event -> assertThat(
                    event.getFormattedMessage())
                    .contains(
                            "service=educloud-gateway",
                            "environment=unknown",
                            "instance=unknown",
                            "requestId=unavailable",
                            "category=ratelimit_dependency",
                            "routeGroup=catalog")
                    .doesNotContain("token", "password", "198.51.100.22"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static void assertCounter(
            SimpleMeterRegistry registry, String name, String... tags) {
        Counter counter = registry.get(name).tags(tags).counter();
        assertThat(counter.count()).isEqualTo(1);
    }
}
