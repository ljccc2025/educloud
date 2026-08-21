package com.educloud.gateway.route;

public record AccessDecision(AccessKind kind, String routeGroup) {

    public boolean mayProceedWithoutBearer() {
        return kind == AccessKind.PUBLIC_READ
                || kind == AccessKind.AUTH_SENSITIVE
                || kind == AccessKind.PAYMENT_CALLBACK
                || kind == AccessKind.ACTUATOR_HEALTH;
    }
}
