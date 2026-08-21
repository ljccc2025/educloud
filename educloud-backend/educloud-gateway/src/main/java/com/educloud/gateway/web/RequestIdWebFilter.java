package com.educloud.gateway.web;

import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestIdPolicy;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@Component
public final class RequestIdWebFilter implements WebFilter, Ordered {

    private final RequestIdPolicy requestIdPolicy;

    public RequestIdWebFilter() {
        this(new RequestIdPolicy(UUID::randomUUID));
    }

    RequestIdWebFilter(RequestIdPolicy requestIdPolicy) {
        this.requestIdPolicy = Objects.requireNonNull(requestIdPolicy, "requestIdPolicy");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = requestIdPolicy.resolve(
                exchange.getRequest().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, requestId);
        exchange.getResponse().getHeaders().set(RequestContext.REQUEST_ID_HEADER, requestId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(RequestContext.REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(RequestContext.REQUEST_ID_HEADER, requestId))
                .build();
        ServerWebExchange filteredExchange = exchange.mutate().request(request).build();

        return chain.filter(filteredExchange)
                .contextWrite(context -> context.put(
                        GatewayExchangeAttributes.REACTOR_CONTEXT_REQUEST_ID, requestId));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.REQUEST_ID;
    }
}
