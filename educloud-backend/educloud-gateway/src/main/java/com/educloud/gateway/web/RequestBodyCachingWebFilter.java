package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.route.AccessDecision;
import com.educloud.gateway.route.AccessKind;
import com.educloud.gateway.route.AccessPolicy;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public final class RequestBodyCachingWebFilter implements WebFilter, Ordered {

    private final AccessPolicy accessPolicy;
    private final GatewayWebProperties properties;
    private final GatewayErrorWriter errorWriter;

    public RequestBodyCachingWebFilter(
            AccessPolicy accessPolicy,
            GatewayWebProperties properties,
            GatewayErrorWriter errorWriter) {
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        int limit = Math.toIntExact(bodyLimit(exchange));
        long declaredLength = exchange.getRequest().getHeaders().getContentLength();
        if (declaredLength > limit) {
            return tooLarge(exchange);
        }

        Mono<BodyRead> bodyRead = readBody(exchange, limit)
                .map(bytes -> (BodyRead) new PresentBody(bytes))
                .defaultIfEmpty(AbsentBody.INSTANCE)
                .onErrorResume(DataBufferLimitException.class,
                        exception -> Mono.just(TooLargeBody.INSTANCE));

        return bodyRead.flatMap(result -> {
            if (result == TooLargeBody.INSTANCE) {
                return tooLarge(exchange);
            }
            if (result == AbsentBody.INSTANCE) {
                return chain.filter(exchange);
            }
            byte[] bytes = ((PresentBody) result).bytes();
            GatewayExchangeAttributes.cacheRequestBody(exchange, bytes);
            ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return Flux.defer(() -> Mono.just(
                            exchange.getResponse().bufferFactory().wrap(bytes)));
                }
            };
            return chain.filter(exchange.mutate().request(decorated).build());
        });
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.BODY_CACHE;
    }

    private Mono<byte[]> readBody(ServerWebExchange exchange, int limit) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), limit)
                .handle((buffer, sink) -> {
                    try {
                        int size = buffer.readableByteCount();
                        if (size > 0) {
                            byte[] bytes = new byte[size];
                            buffer.read(bytes);
                            sink.next(bytes);
                        }
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                });
    }

    private long bodyLimit(ServerWebExchange exchange) {
        AccessDecision access = accessPolicy.classify(
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().pathWithinApplication());
        if (access.kind() == AccessKind.AUTH_SENSITIVE) {
            return properties.getAuthBodyLimit().toBytes();
        }
        if (access.kind() == AccessKind.PAYMENT_CALLBACK) {
            return properties.getPaymentCallbackBodyLimit().toBytes();
        }
        return properties.getGlobalBodyLimit().toBytes();
    }

    private Mono<Void> tooLarge(ServerWebExchange exchange) {
        return errorWriter.write(
                exchange, GatewayFailure.of(GatewayErrorCode.GATEWAY_REQUEST_TOO_LARGE));
    }

    private sealed interface BodyRead permits PresentBody, AbsentBody, TooLargeBody {
    }

    private record PresentBody(byte[] bytes) implements BodyRead {
    }

    private enum AbsentBody implements BodyRead {
        INSTANCE
    }

    private enum TooLargeBody implements BodyRead {
        INSTANCE
    }
}
