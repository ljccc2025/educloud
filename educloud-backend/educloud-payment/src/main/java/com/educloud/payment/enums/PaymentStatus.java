package com.educloud.payment.enums;

import java.util.Set;

public enum PaymentStatus {
    INITIATED,
    PAYING,
    SUCCESS,
    FAILED,
    CLOSED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CLOSED;
    }

    public boolean canTransitionTo(PaymentStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case INITIATED -> Set.of(PAYING, SUCCESS, FAILED, CLOSED).contains(target);
            case PAYING -> Set.of(SUCCESS, FAILED, CLOSED).contains(target);
            case SUCCESS, FAILED, CLOSED -> false;
        };
    }
}
