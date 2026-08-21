package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public final class OriginPolicyWebFilter implements WebFilter, Ordered {

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final Set<String> ALLOWED_REQUEST_HEADERS = Set.of(
            "authorization",
            "content-type",
            "x-request-id",
            "idempotency-key",
            "if-match",
            "accept-language");

    private final Set<String> allowedOrigins;
    private final GatewayErrorWriter errorWriter;

    public OriginPolicyWebFilter(
            GatewayWebProperties properties, GatewayErrorWriter errorWriter) {
        Objects.requireNonNull(properties, "properties");
        this.allowedOrigins = Set.copyOf(properties.getAllowedOrigins());
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        List<String> origins = exchange.getRequest().getHeaders().get(HttpHeaders.ORIGIN);
        if (origins == null || origins.isEmpty()) {
            return chain.filter(exchange);
        }
        if (origins.size() != 1
                || origins.get(0).contains(",")
                || !allowedOrigins.contains(origins.get(0))) {
            return denied(exchange);
        }

        String requestedMethod = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        if (requestedMethod != null) {
            if (!HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
                    || !ALLOWED_METHODS.contains(requestedMethod.toUpperCase(Locale.ROOT))) {
                return denied(exchange);
            }
            if (!requestedHeadersAllowed(exchange.getRequest().getHeaders()
                    .get(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS))) {
                return denied(exchange);
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.ORIGIN;
    }

    private Mono<Void> denied(ServerWebExchange exchange) {
        return errorWriter.write(exchange, GatewayFailure.of(GatewayErrorCode.ACCESS_DENIED));
    }

    private static boolean requestedHeadersAllowed(List<String> values) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        Set<String> requested = new HashSet<>();
        for (String value : values) {
            if (value == null) {
                return false;
            }
            boolean valid = Arrays.stream(value.split(",", -1))
                    .map(String::trim)
                    .allMatch(header -> !header.isEmpty()
                            && requested.add(header.toLowerCase(Locale.ROOT))
                            && ALLOWED_REQUEST_HEADERS.contains(header.toLowerCase(Locale.ROOT)));
            if (!valid) {
                return false;
            }
        }
        return true;
    }
}
