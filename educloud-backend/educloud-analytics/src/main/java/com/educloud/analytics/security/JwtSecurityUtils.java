package com.educloud.analytics.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class JwtSecurityUtils {

    private JwtSecurityUtils() {}

    public static String extractOperator(Jwt jwt, HttpServletRequest request) {
        if (jwt != null) {
            String username = jwt.getClaimAsString("username");
            if (StringUtils.hasText(username)) {
                return username.trim();
            }
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (StringUtils.hasText(preferredUsername)) {
                return preferredUsername.trim();
            }
            String nickname = jwt.getClaimAsString("nickname");
            if (StringUtils.hasText(nickname)) {
                return nickname.trim();
            }
            String subject = jwt.getSubject();
            if (StringUtils.hasText(subject)) {
                return subject.trim();
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt principalJwt) {
                return extractOperator(principalJwt, request);
            }
            String name = authentication.getName();
            if (StringUtils.hasText(name) && !"anonymousUser".equalsIgnoreCase(name)) {
                return name.trim();
            }
        }

        if (request != null) {
            String operatorHeader = request.getHeader("X-Operator");
            if (StringUtils.hasText(operatorHeader)) {
                return operatorHeader.trim();
            }
            String usernameHeader = request.getHeader("X-User-Name");
            if (StringUtils.hasText(usernameHeader)) {
                return usernameHeader.trim();
            }
            String clientIdHeader = request.getHeader("X-Client-Id");
            if (StringUtils.hasText(clientIdHeader)) {
                return clientIdHeader.trim();
            }
        }

        return "admin";
    }

    /**
     * 解析当前登录教师 ID：身份必须唯一来源于已验证 JWT 的 subject。
     * 请求头（X-Teacher-Id/X-User-Id）不可作为身份依据——网关不会注入这些头，
     * 客户端直接伪造即可越权读取他人动态（IDOR）；无有效 JWT 返回 null（
     * 查询接口按空主体返回空列表）。
     */
    public static String extractTeacherId(Jwt jwt, HttpServletRequest request) {
        if (jwt != null) {
            String subject = jwt.getSubject();
            if (StringUtils.hasText(subject)) {
                return subject.trim();
            }
        }
        return null;
    }

    /**
     * 解析当前登录学员 ID：身份必须唯一来源于已验证 JWT 的 subject。
     * 请求头（X-Student-Id/X-User-Id）不可作为身份依据——网关不会注入这些头，
     * 客户端直接伪造即可越权读取他人学习动态（含作业分数，IDOR）；无有效
     * JWT 返回 null（查询接口按空主体返回空列表）。
     */
    public static String extractStudentId(Jwt jwt, HttpServletRequest request) {
        if (jwt != null) {
            String subject = jwt.getSubject();
            if (StringUtils.hasText(subject)) {
                return subject.trim();
            }
        }
        return null;
    }

    public static Long userId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            return null;
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Set<String> roles(Jwt jwt) {
        if (jwt == null) {
            return Set.of();
        }
        Object value = jwt.getClaim("roles");
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
