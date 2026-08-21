package com.educloud.gateway.observability;

import com.educloud.gateway.route.RouteGroups;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public final class MicrometerGatewayMetrics implements GatewayMetrics {

    private static final Set<String> ROUTE_GROUPS = Set.of(
            RouteGroups.AUTH,
            RouteGroups.CATALOG,
            RouteGroups.PAYMENT_CALLBACK,
            RouteGroups.LIVE_WS,
            RouteGroups.USER,
            RouteGroups.COURSE,
            RouteGroups.CONTENT,
            RouteGroups.ORDER,
            RouteGroups.PAYMENT,
            RouteGroups.LIVE,
            RouteGroups.FILE,
            RouteGroups.NOTIFICATION,
            RouteGroups.ANALYTICS,
            RouteGroups.SEARCH,
            RouteGroups.RECOMMENDATION,
            RouteGroups.UNMATCHED);

    private final MeterRegistry registry;

    public MicrometerGatewayMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void recordSecurityFailure(SecurityFailureCategory category, String routeId) {
        counter("gateway.security.failures",
                "category", value(category),
                "routeId", GatewayExchangeAttributes.sanitizeRouteId(routeId)).increment();
    }

    @Override
    public void recordSessionCheck(SessionResult result, String routeId) {
        counter("gateway.session.checks",
                "result", value(result),
                "routeId", GatewayExchangeAttributes.sanitizeRouteId(routeId)).increment();
    }

    @Override
    public void recordRateLimitDecision(RateLimitResult result, String routeGroup) {
        counter("gateway.ratelimit.decisions",
                "result", value(result),
                "routeGroup", routeGroup(routeGroup)).increment();
    }

    @Override
    public void recordRateLimitDegraded(String routeGroup) {
        counter("gateway.ratelimit.degraded",
                "routeGroup", routeGroup(routeGroup)).increment();
    }

    @Override
    public void recordDependency(Dependency dependency, DependencyResult result) {
        counter("gateway.dependencies",
                "dependency", value(dependency),
                "result", value(result)).increment();
    }

    @Override
    public void recordRequest(
            HttpMethod method,
            int status,
            RequestCategory category,
            String routeId,
            String environment,
            Duration duration) {
        String statusTag = status >= 100 && status <= 599 ? Integer.toString(status) : "unknown";
        Timer.builder("gateway.requests")
                .tags(
                        "service", "educloud-gateway",
                        "environment", safeEnvironment(environment),
                        "routeId", GatewayExchangeAttributes.sanitizeRouteId(routeId),
                        "method", method == null ? "UNKNOWN" : method.name(),
                        "status", statusTag,
                        "category", value(category))
                .register(registry)
                .record(duration);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private static String routeGroup(String candidate) {
        return candidate != null && ROUTE_GROUPS.contains(candidate)
                ? candidate
                : RouteGroups.UNMATCHED;
    }

    private static String safeEnvironment(String candidate) {
        return candidate != null && candidate.matches("[a-z0-9-]{1,32}") ? candidate : "unknown";
    }

    private static String value(Enum<?> value) {
        return Objects.requireNonNull(value, "metric enum").name().toLowerCase(Locale.ROOT);
    }
}
