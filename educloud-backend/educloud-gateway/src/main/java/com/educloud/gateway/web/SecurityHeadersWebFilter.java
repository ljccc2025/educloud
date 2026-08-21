package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public final class SecurityHeadersWebFilter implements WebFilter, Ordered {

    private static final String HSTS = "Strict-Transport-Security";
    private static final List<String> VERSION_HEADERS = List.of(
            HttpHeaders.SERVER,
            "X-Powered-By",
            "X-Application-Context",
            "X-AspNet-Version",
            "X-AspNetMvc-Version");

    private final boolean localEnvironment;

    public SecurityHeadersWebFilter(GatewayRuntimeProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.localEnvironment = "local".equals(properties.environment());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            applySecurityHeaders(exchange);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.SECURITY_HEADERS;
    }

    private void applySecurityHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        VERSION_HEADERS.forEach(headers::remove);
        setIfAbsent(headers, "X-Content-Type-Options", "nosniff");
        setIfAbsent(headers, "X-Frame-Options", "DENY");
        setIfAbsent(headers, "Referrer-Policy", "no-referrer");
        setIfAbsent(headers, "Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        setIfAbsent(headers, "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        String scheme = exchange.getRequest().getURI().getScheme();
        if (!localEnvironment && scheme != null && "https".equals(scheme.toLowerCase(Locale.ROOT))) {
            setIfAbsent(headers, HSTS, "max-age=31536000; includeSubDomains");
        }
    }

    private static void setIfAbsent(HttpHeaders headers, String name, String value) {
        if (!headers.containsKey(name)) {
            headers.set(name, value);
        }
    }
}
