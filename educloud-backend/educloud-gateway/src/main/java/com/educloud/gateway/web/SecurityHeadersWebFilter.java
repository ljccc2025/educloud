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
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class SecurityHeadersWebFilter implements WebFilter, Ordered {

    private static final String HSTS = "Strict-Transport-Security";
    private static final String HSTS_BASELINE = "max-age=31536000; includeSubDomains";
    private static final String PERMISSIONS_BASELINE = "camera=(), microphone=(), geolocation=()";
    private static final String CSP_BASELINE = "default-src 'none'; frame-ancestors 'none'";
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
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        if (!hasRequiredPermissions(headers.getFirst("Permissions-Policy"))) {
            headers.set("Permissions-Policy", PERMISSIONS_BASELINE);
        }
        if (!hasRequiredCspDirectives(headers.getFirst("Content-Security-Policy"))) {
            headers.set("Content-Security-Policy", CSP_BASELINE);
        }
        String scheme = exchange.getRequest().getURI().getScheme();
        if (!localEnvironment && scheme != null && "https".equals(scheme.toLowerCase(Locale.ROOT))) {
            if (!hasStrongHsts(headers.getFirst(HSTS))) {
                headers.set(HSTS, HSTS_BASELINE);
            }
        }
    }

    private static boolean hasRequiredPermissions(String value) {
        if (value == null) {
            return false;
        }
        Set<String> directives = List.of(value.toLowerCase(Locale.ROOT).split(",", -1)).stream()
                .map(directive -> directive.replaceAll("\\s+", ""))
                .collect(Collectors.toSet());
        return directives.containsAll(Set.of("camera=()", "microphone=()", "geolocation=()"));
    }

    private static boolean hasRequiredCspDirectives(String value) {
        if (value == null) {
            return false;
        }
        Set<String> directives = List.of(value.toLowerCase(Locale.ROOT).split(";", -1)).stream()
                .map(String::trim)
                .collect(Collectors.toSet());
        return directives.contains("default-src 'none'")
                && directives.contains("frame-ancestors 'none'");
    }

    private static boolean hasStrongHsts(String value) {
        if (value == null) {
            return false;
        }
        boolean includeSubDomains = false;
        long maxAge = -1;
        for (String rawDirective : value.split(";", -1)) {
            String directive = rawDirective.trim();
            if ("includesubdomains".equalsIgnoreCase(directive)) {
                includeSubDomains = true;
            } else if (directive.regionMatches(true, 0, "max-age=", 0, "max-age=".length())) {
                String seconds = directive.substring("max-age=".length()).trim();
                if (!seconds.matches("[0-9]{1,18}")) {
                    return false;
                }
                try {
                    maxAge = Long.parseLong(seconds);
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return includeSubDomains && maxAge >= 31_536_000;
    }
}
