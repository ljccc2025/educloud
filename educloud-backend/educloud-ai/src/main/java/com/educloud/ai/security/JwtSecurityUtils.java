package com.educloud.ai.security;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class JwtSecurityUtils {

    private JwtSecurityUtils() {
    }

    /** 身份只取 JWT sub（规格 §9.1）：任何接口不接受前端传 studentId。 */
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

    public static Set<String> roles(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
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
}
