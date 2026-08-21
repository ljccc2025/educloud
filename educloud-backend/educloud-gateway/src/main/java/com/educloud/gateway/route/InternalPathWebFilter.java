package com.educloud.gateway.route;

import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.web.GatewayFilterOrders;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public final class InternalPathWebFilter implements WebFilter, Ordered {

    private final AccessPolicy accessPolicy;
    private final GatewayErrorWriter errorWriter;

    public InternalPathWebFilter(AccessPolicy accessPolicy, GatewayErrorWriter errorWriter) {
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AccessDecision access = accessPolicy.classify(
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().pathWithinApplication());
        if (access.kind() == AccessKind.INTERNAL) {
            return errorWriter.write(exchange,
                    GatewayFailure.of(GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.INTERNAL_PATH;
    }
}
