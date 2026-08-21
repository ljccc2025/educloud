package com.educloud.gateway.web;

import com.educloud.common.web.RequestContext;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;
import java.util.Set;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

public final class GatewayExchangeAttributes {

    public static final String REQUEST_ID = RequestContext.REQUEST_ID_ATTRIBUTE;
    public static final String REACTOR_CONTEXT_REQUEST_ID = "requestId";
    public static final String ACCESS_DECISION = GatewayExchangeAttributes.class.getName() + ".accessDecision";
    public static final String CLIENT_IP = GatewayExchangeAttributes.class.getName() + ".clientIp";

    private static final String CACHED_REQUEST_BODY =
            GatewayExchangeAttributes.class.getName() + ".cachedRequestBody";

    private static final Set<String> ROUTE_IDS = Set.of(
            "user-core",
            "user-me",
            "content-me",
            "course-enrollments",
            "content-course-scoped",
            "content-core",
            "content-drafts",
            "course-core",
            "order-core",
            "payment-core",
            "live-http",
            "live-ws",
            "file-core",
            "notification-core",
            "analytics-core",
            "search-core",
            "recommendation-core");

    private GatewayExchangeAttributes() {
    }

    public static String requireRequestId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(REQUEST_ID);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        throw new IllegalStateException("requestId is not available on the exchange");
    }

    public static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return sanitizeRouteId(route == null ? null : route.getId());
    }

    public static String sanitizeRouteId(String routeId) {
        return routeId != null && ROUTE_IDS.contains(routeId) ? routeId : "unmatched";
    }

    public static void cacheRequestBody(ServerWebExchange exchange, byte[] body) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("cached request body must not be empty");
        }
        exchange.getAttributes().put(CACHED_REQUEST_BODY, body.clone());
    }

    public static Optional<byte[]> cachedRequestBody(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(CACHED_REQUEST_BODY);
        if (value instanceof byte[] body) {
            return Optional.of(body.clone());
        }
        return Optional.empty();
    }

    public static void clearCachedRequestBody(ServerWebExchange exchange) {
        exchange.getAttributes().remove(CACHED_REQUEST_BODY);
    }
}
