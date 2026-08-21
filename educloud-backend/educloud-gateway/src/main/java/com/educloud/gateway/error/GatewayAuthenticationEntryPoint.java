package com.educloud.gateway.error;

import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public final class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final GatewayErrorWriter errorWriter;
    private final GatewayMetrics metrics;

    @Autowired
    public GatewayAuthenticationEntryPoint(
            GatewayErrorWriter errorWriter, ObjectProvider<GatewayMetrics> metricsProvider) {
        this(errorWriter, metricsProvider.getIfAvailable(GatewayMetrics::noOp));
    }

    GatewayAuthenticationEntryPoint(GatewayErrorWriter errorWriter, GatewayMetrics metrics) {
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        metrics.recordSecurityFailure(
                GatewayMetrics.SecurityFailureCategory.AUTHENTICATION,
                GatewayExchangeAttributes.routeId(exchange));
        return errorWriter.write(exchange, GatewayFailure.of(GatewayErrorCode.UNAUTHENTICATED));
    }
}
