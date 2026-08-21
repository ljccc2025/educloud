package com.educloud.gateway.observability;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.educloud.gateway.web.GatewayFilterOrders;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class GatewayObservationWebFilter implements WebFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayObservationWebFilter.class);

    private final GatewayMetrics metrics;
    private final String environment;
    private final String instance;
    private final Tracer tracer;

    @Autowired
    public GatewayObservationWebFilter(
            GatewayMetrics metrics,
            GatewayRuntimeProperties runtimeProperties,
            @Value("${spring.application.instance_id:${HOSTNAME:unknown}}") String instance,
            ObjectProvider<Tracer> tracerProvider) {
        this(metrics, runtimeProperties, instance, tracerProvider.getIfAvailable());
    }

    GatewayObservationWebFilter(
            GatewayMetrics metrics,
            GatewayRuntimeProperties runtimeProperties,
            String instance,
            Tracer tracer) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.environment = Objects.requireNonNull(runtimeProperties, "runtimeProperties").environment();
        this.instance = safeInstance(instance);
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedNanos = System.nanoTime();
        AtomicBoolean recorded = new AtomicBoolean();
        String traceId = currentTraceId();
        exchange.getResponse().beforeCommit(() -> {
            record(exchange, startedNanos, traceId, false, recorded);
            return Mono.empty();
        });
        return chain.filter(exchange)
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL) {
                        record(exchange, startedNanos, traceId, true, recorded);
                    }
                });
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.OBSERVATION;
    }

    private void record(
            ServerWebExchange exchange,
            long startedNanos,
            String traceId,
            boolean cancelled,
            AtomicBoolean recorded) {
        if (!recorded.compareAndSet(false, true)) {
            return;
        }
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode == null ? 0 : statusCode.value();
        GatewayMetrics.RequestCategory category = category(status, cancelled);
        Duration duration = Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos));
        HttpMethod method = exchange.getRequest().getMethod();
        String routeId = GatewayExchangeAttributes.routeId(exchange);
        metrics.recordRequest(method, status, category, routeId, environment, duration);
        Object requestId = exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID);
        LOGGER.info(
                "gateway_request service=educloud-gateway environment={} instance={} requestId={} traceId={} routeId={} method={} status={} durationMs={} category={}",
                environment,
                instance,
                requestId instanceof String value ? value : "unavailable",
                traceId,
                routeId,
                method == null ? "UNKNOWN" : method.name(),
                status,
                duration.toMillis(),
                category.name());
    }

    private String currentTraceId() {
        Span span = tracer == null ? null : tracer.currentSpan();
        return span == null ? "unavailable" : span.context().traceId();
    }

    private static GatewayMetrics.RequestCategory category(int status, boolean cancelled) {
        if (cancelled) {
            return GatewayMetrics.RequestCategory.CANCELLED;
        }
        if (status >= 500 || status == 0) {
            return GatewayMetrics.RequestCategory.SERVER_ERROR;
        }
        if (status >= 400) {
            return GatewayMetrics.RequestCategory.CLIENT_ERROR;
        }
        return GatewayMetrics.RequestCategory.SUCCESS;
    }

    private static String safeInstance(String candidate) {
        return candidate != null && candidate.matches("[A-Za-z0-9._-]{1,128}")
                ? candidate
                : "unknown";
    }
}
