package com.educloud.content.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class InternalApiFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = InternalApiFilter.class.getName() + ".clientId";

    private final JwtDecoder jwtDecoder;
    private final ContentProperties contentProperties;

    public InternalApiFilter(JwtDecoder jwtDecoder, ContentProperties contentProperties) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.contentProperties = Objects.requireNonNull(contentProperties, "contentProperties");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/");
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
        String effectiveAudience = contentProperties.internal() != null ? contentProperties.internal().effectiveInternalAudience() : "educloud-content";
        if (!(clientId instanceof String clientIdText)
                || !token.getAudience().contains(effectiveAudience)
                || (contentProperties.internal() != null && !contentProperties.internal().effectiveAllowedClientIds().isEmpty() && !contentProperties.internal().effectiveAllowedClientIds().contains(clientIdText))) {
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
