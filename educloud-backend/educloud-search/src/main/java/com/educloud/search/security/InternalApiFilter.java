package com.educloud.search.security;

import com.educloud.search.config.SearchProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * 内部 API 通信安全过滤器（拦截 /internal/**）
 * 支持 X-Internal-Token 校验与微服务间内部 Bearer JWT 校验及 Client ID 白名单校验。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class InternalApiFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = InternalApiFilter.class.getName() + ".clientId";

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final SearchProperties properties;

    public InternalApiFilter(ObjectProvider<JwtDecoder> jwtDecoderProvider, SearchProperties properties) {
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
            if (configuredSecret != null && !configuredSecret.isBlank()
                    && MessageDigest.isEqual(
                            configuredSecret.getBytes(StandardCharsets.UTF_8),
                            internalTokenHeader.getBytes(StandardCharsets.UTF_8))) {
                String caller = request.getHeader("X-Client-Id");
                String effectiveCaller = (caller != null && !caller.isBlank()) ? caller.trim() : "internal-service";
                request.setAttribute(CLIENT_ID_ATTRIBUTE, effectiveCaller);
                SecurityContextHolder.getContext().setAuthentication(
                        new InternalApiAuthenticationToken(effectiveCaller, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")))
                );
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

        Jwt token;
        try {
            token = jwtDecoder.decode(authorization.substring(7).trim());
        } catch (JwtException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Object clientId = token.getClaim("clientId");
        String effectiveAudience = properties.getInternal() != null && properties.getInternal().getAudience() != null
                ? properties.getInternal().getAudience()
                : "educloud-search";
        if (!(clientId instanceof String clientIdText)
                || !token.getAudience().contains(effectiveAudience)
                || (properties.getInternal() != null
                    && properties.getInternal().getAllowedClientIds() != null
                    && !properties.getInternal().getAllowedClientIds().isEmpty()
                    && !properties.getInternal().getAllowedClientIds().contains(clientIdText))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        request.setAttribute(CLIENT_ID_ATTRIBUTE, clientIdText);
        SecurityContextHolder.getContext().setAuthentication(
                new InternalApiAuthenticationToken(clientIdText, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")))
        );
        filterChain.doFilter(request, response);
    }
}
