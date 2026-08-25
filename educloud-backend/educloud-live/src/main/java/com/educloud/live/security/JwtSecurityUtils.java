package com.educloud.live.security;

import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class JwtSecurityUtils {

    private JwtSecurityUtils() {
    }

    public static Long userId(Jwt jwt) {
        if (jwt == null) {
            throw new LiveException(LiveErrorCode.UNAUTHENTICATED);
        }
        String subject = jwt.getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new LiveException(LiveErrorCode.UNAUTHENTICATED, "JWT subject 必须为数字 userId: " + subject);
        }
    }

    public static String userName(Jwt jwt) {
        if (jwt == null) {
            return "Anonymous";
        }
        String username = jwt.getClaimAsString("username");
        if (username == null || username.isBlank()) {
            username = jwt.getClaimAsString("nickname");
        }
        return username != null ? username : "User_" + jwt.getSubject();
    }

    public static boolean isAdmin(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        Set<String> roles = roles(jwt);
        return roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
    }

    public static boolean isTeacher(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        Set<String> roles = roles(jwt);
        return roles.contains("TEACHER") || roles.contains("ROLE_TEACHER");
    }

    public static Set<String> roles(Jwt jwt) {
        if (jwt == null) {
            return Set.of();
        }
        Object value = jwt.getClaim("roles");
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item instanceof String text) {
                roles.add(text);
            }
        }
        return Set.copyOf(roles);
    }
}
