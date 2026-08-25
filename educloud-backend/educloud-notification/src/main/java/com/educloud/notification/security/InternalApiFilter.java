package com.educloud.notification.security;

import com.educloud.notification.config.NotificationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
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

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final NotificationProperties properties;

    public InternalApiFilter(ObjectProvider<JwtDecoder> jwtDecoderProvider, NotificationProperties properties) {
        this.jwtDecoderProvider = Objects.requireNonNull(jwtDecoderProvider, "jwtDecoderProvider");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String internalTokenHeader = request.getHeader("X-Internal-Token");
        if (internalTokenHeader != null && !internalTokenHeader.isBlank()) {
            String configuredSecret = properties.getInternal() != null ? properties.getInternal().getSecretToken() : null;
            if (configuredSecret != null && !configuredSecret.isBlank() && configuredSecret.equals(internalTokenHeader)) {
                String caller = request.getHeader("X-Client-Id");
                request.setAttribute(CLIENT_ID_ATTRIBUTE, caller != null ? caller : "internal-service");
                filterChain.doFilter(request, response);
                return;
            }
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
        if (jwtDecoder == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            jwtDecoder.decode(authorization.substring(7).trim());
            filterChain.doFilter(request, response);
        } catch (JwtException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
