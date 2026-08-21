package com.educloud.user.security;

import com.educloud.user.config.InternalProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 内部接口服务身份过滤器（/internal/v1/**）。依据：安全设计第 8 节与 M03 设计规格第 15 节
 * （服务 Token 验签、aud 必须为本服务、clientId 白名单；仅在内网可达不是授权）。
 */
@Component
public final class InternalApiFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = InternalApiFilter.class.getName() + ".clientId";

    private final JwtDecoder jwtDecoder;
    private final InternalProperties internalProperties;

    public InternalApiFilter(JwtDecoder jwtDecoder, InternalProperties internalProperties) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.internalProperties = Objects.requireNonNull(internalProperties, "internalProperties");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Jwt token;
        try {
            token = jwtDecoder.decode(authorization.substring(7).trim());
        } catch (JwtException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Object clientId = token.getClaim("clientId");
        if (!(clientId instanceof String clientIdText)
                || !token.getAudience().contains("educloud-user")
                || !internalProperties.allowedClientIds().contains(clientIdText)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        request.setAttribute(CLIENT_ID_ATTRIBUTE, clientIdText);
        filterChain.doFilter(request, response);
    }

    public static String requireClientId(HttpServletRequest request) {
        Object clientId = request.getAttribute(CLIENT_ID_ATTRIBUTE);
        if (!(clientId instanceof String text)) {
            throw new IllegalStateException("InternalApiFilter must run before internal controllers");
        }
        return text;
    }
}
