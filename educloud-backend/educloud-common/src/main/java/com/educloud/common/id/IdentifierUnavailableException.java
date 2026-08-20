package com.educloud.common.id;

/** Indicates that identifier generation has failed closed and must not be retried blindly. */
public final class IdentifierUnavailableException extends IllegalStateException {

    public IdentifierUnavailableException(String message) {
        super(message);
    }

    public IdentifierUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
