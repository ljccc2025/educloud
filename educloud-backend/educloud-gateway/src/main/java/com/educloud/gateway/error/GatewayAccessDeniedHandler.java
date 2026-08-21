package com.educloud.gateway.error;

import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public final class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final GatewayErrorWriter errorWriter;
    private final GatewayMetrics metrics;

    @Autowired
    public GatewayAccessDeniedHandler(
            GatewayErrorWriter errorWriter, ObjectProvider<GatewayMetrics> metricsProvider) {
        this(errorWriter, metricsProvider.getIfAvailable(GatewayMetrics::noOp));
    }

    GatewayAccessDeniedHandler(GatewayErrorWriter errorWriter, GatewayMetrics metrics) {
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        metrics.recordSecurityFailure(
                GatewayMetrics.SecurityFailureCategory.AUTHORIZATION,
                GatewayExchangeAttributes.routeId(exchange));
        return errorWriter.write(exchange, GatewayFailure.of(GatewayErrorCode.ACCESS_DENIED));
    }
}
