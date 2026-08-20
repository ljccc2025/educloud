package com.educloud.common.security;

import java.util.Objects;
import java.util.Set;

public record AuthenticatedUser(
        String userId,
        String sessionId,
        Set<String> roles,
        Set<String> permissions) {

    public AuthenticatedUser {
        userId = requireText(userId, "userId");
        sessionId = requireText(sessionId, "sessionId");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
