package com.educloud.gateway.route;

public enum AccessKind {
    INTERNAL,
    ACTUATOR_HEALTH,
    PUBLIC_READ,
    AUTH_SENSITIVE,
    PAYMENT_CALLBACK,
    PROTECTED
}
