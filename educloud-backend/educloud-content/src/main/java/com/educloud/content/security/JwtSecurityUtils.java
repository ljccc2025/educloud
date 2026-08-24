package com.educloud.content.security;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.security.AuthenticatedUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class JwtSecurityUtils {

    private JwtSecurityUtils() {
    }

    public static Long userId(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        String subject = jwt.getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT subject must be a numeric userId: " + subject);
        }
    }

    public static Set<String> permissions(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        Object value = jwt.getClaim("permissions");
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT permissions claim must be an array of strings");
        }
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        for (Object item : collection) {
            if (!(item instanceof String text)) {
                throw new BusinessException(
                        CommonErrorCode.UNAUTHENTICATED,
                        "JWT permissions claim must be an array of strings");
            }
            permissions.add(text);
        }
        return Set.copyOf(permissions);
    }

    public static boolean hasPermission(Jwt jwt, String permission) {
        return permissions(jwt).contains(permission);
    }

    public static Set<String> roles(Jwt jwt) {
        if (jwt == null) return Set.of();
        Object value = jwt.getClaim("roles");
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT roles claim must be an array of strings");
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (Object item : collection) {
            if (!(item instanceof String text)) {
                throw new BusinessException(
                        CommonErrorCode.UNAUTHENTICATED,
                        "JWT roles claim must be an array of strings");
            }
            roles.add(text);
        }
        return Set.copyOf(roles);
    }

    public static AuthenticatedUser authenticatedUser(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        Object sidValue = jwt.getClaim("sid");
        String sessionId = (sidValue instanceof String text) ? text : "default";
        return new AuthenticatedUser(jwt.getSubject(), sessionId, roles(jwt), permissions(jwt));
    }
}
