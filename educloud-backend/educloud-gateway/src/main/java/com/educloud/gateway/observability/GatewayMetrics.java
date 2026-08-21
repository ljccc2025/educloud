package com.educloud.gateway.observability;

import org.springframework.http.HttpMethod;

import java.time.Duration;

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

    enum SessionResult {
        ACTIVE,
        REJECTED,
        CORRUPT,
        DEPENDENCY_ERROR
    }

    enum Dependency {
        JWKS,
        REDIS,
        NACOS
    }

    enum DependencyResult {
        UP,
        DOWN
    }

    enum RequestCategory {
        SUCCESS,
        CLIENT_ERROR,
        SERVER_ERROR,
        CANCELLED
    }

    void recordSecurityFailure(SecurityFailureCategory category, String routeId);

    default void recordRateLimitDecision(RateLimitResult result, String routeGroup) {
    }

    default void recordRateLimitDegraded(String routeGroup) {
    }

    default void recordSessionCheck(SessionResult result, String routeId) {
    }

    default void recordDependency(Dependency dependency, DependencyResult result) {
    }

    default void recordRequest(
            HttpMethod method,
            int status,
            RequestCategory category,
            String routeId,
            String environment,
            Duration duration) {
    }

    static GatewayMetrics noOp() {
        return (category, routeId) -> { };
    }
}
