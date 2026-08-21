package com.educloud.gateway.observability;

public interface GatewayMetrics {

    enum SecurityFailureCategory {
        AUTHENTICATION,
        AUTHORIZATION
    }

    void recordSecurityFailure(SecurityFailureCategory category, String routeId);

    static GatewayMetrics noOp() {
        return (category, routeId) -> { };
    }
}
