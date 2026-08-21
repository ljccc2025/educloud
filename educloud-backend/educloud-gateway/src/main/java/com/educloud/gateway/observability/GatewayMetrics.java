package com.educloud.gateway.observability;

public interface GatewayMetrics {

    enum SecurityFailureCategory {
        AUTHENTICATION,
        AUTHORIZATION
    }

    enum RateLimitResult {
        ALLOWED,
        DENIED,
        DEPENDENCY_ERROR
    }

    void recordSecurityFailure(SecurityFailureCategory category, String routeId);

    default void recordRateLimitDecision(RateLimitResult result, String routeGroup) {
    }

    default void recordRateLimitDegraded(String routeGroup) {
    }

    static GatewayMetrics noOp() {
        return (category, routeId) -> { };
    }
}
