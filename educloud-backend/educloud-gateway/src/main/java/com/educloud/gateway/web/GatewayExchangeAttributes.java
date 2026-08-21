package com.educloud.gateway.web;

import com.educloud.common.web.RequestContext;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

import java.util.Set;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

public final class GatewayExchangeAttributes {

    public static final String REQUEST_ID = RequestContext.REQUEST_ID_ATTRIBUTE;
    public static final String REACTOR_CONTEXT_REQUEST_ID = "requestId";
    public static final String ACCESS_DECISION = GatewayExchangeAttributes.class.getName() + ".accessDecision";
    public static final String CLIENT_IP = GatewayExchangeAttributes.class.getName() + ".clientIp";

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
        if (route != null && ROUTE_IDS.contains(route.getId())) {
            return route.getId();
        }
        return "unmatched";
    }
}
