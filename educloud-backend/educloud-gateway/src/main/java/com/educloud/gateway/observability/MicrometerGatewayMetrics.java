package com.educloud.gateway.observability;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import com.educloud.gateway.route.RouteGroups;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public final class MicrometerGatewayMetrics implements GatewayMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerGatewayMetrics.class);
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

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
    private final String environment;
    private final String instance;
    private final AtomicLong lastSessionWarning = new AtomicLong();
    private final AtomicLong lastRateLimitWarning = new AtomicLong();
    private final EnumMap<Dependency, AtomicLong> lastDependencyWarnings =
            new EnumMap<>(Dependency.class);

    public MicrometerGatewayMetrics(MeterRegistry registry) {
        this(registry, null, null);
    }

    @Autowired
    public MicrometerGatewayMetrics(
            MeterRegistry registry,
            GatewayRuntimeProperties runtimeProperties,
            @Value("${spring.application.instance_id:${HOSTNAME:unknown}}") String instance) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.environment = safeEnvironment(
                runtimeProperties == null ? null : runtimeProperties.environment());
        this.instance = safeInstance(instance);
        for (Dependency dependency : Dependency.values()) {
            lastDependencyWarnings.put(dependency, new AtomicLong());
        }
    }

    @Override
    public void recordSecurityFailure(SecurityFailureCategory category, String routeId) {
        counter("gateway.security.failures",
                "category", value(category),
                "routeId", GatewayExchangeAttributes.sanitizeRouteId(routeId)).increment();
    }

    @Override
    public void recordSessionCheck(SessionResult result, String routeId) {
        String safeRouteId = GatewayExchangeAttributes.sanitizeRouteId(routeId);
        counter("gateway.session.checks",
                "result", value(result),
                "routeId", safeRouteId).increment();
        if ((result == SessionResult.CORRUPT || result == SessionResult.DEPENDENCY_ERROR)
                && shouldWarn(lastSessionWarning)) {
            LOGGER.warn(
                    "gateway_dependency service=educloud-gateway environment={} instance={} requestId=unavailable traceId=unavailable category=session_{} routeId={}",
                    environment, instance, value(result), safeRouteId);
        }
    }

    @Override
    public void recordRateLimitDecision(RateLimitResult result, String routeGroup) {
        String safeRouteGroup = routeGroup(routeGroup);
        counter("gateway.ratelimit.decisions",
                "result", value(result),
                "routeGroup", safeRouteGroup).increment();
        if (result == RateLimitResult.DEPENDENCY_ERROR && shouldWarn(lastRateLimitWarning)) {
            LOGGER.warn(
                    "gateway_dependency service=educloud-gateway environment={} instance={} requestId=unavailable traceId=unavailable category=ratelimit_dependency routeGroup={}",
                    environment, instance, safeRouteGroup);
        }
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
        if (result == DependencyResult.DOWN
                && shouldWarn(lastDependencyWarnings.get(dependency))) {
            LOGGER.warn(
                    "gateway_dependency service=educloud-gateway environment={} instance={} requestId=unavailable traceId=unavailable category=readiness_dependency dependency={}",
                    environment, instance, value(dependency));
        }
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

    private static String safeInstance(String candidate) {
        return candidate != null && candidate.matches("[A-Za-z0-9._-]{1,128}")
                ? candidate
                : "unknown";
    }

    private static boolean shouldWarn(AtomicLong lastWarning) {
        long now = System.nanoTime();
        long previous = lastWarning.get();
        boolean intervalElapsed = previous == 0
                || now < previous
                || now - previous >= WARNING_INTERVAL_NANOS;
        return intervalElapsed && lastWarning.compareAndSet(previous, now);
    }

    private static String value(Enum<?> value) {
        return Objects.requireNonNull(value, "metric enum").name().toLowerCase(Locale.ROOT);
    }
}
