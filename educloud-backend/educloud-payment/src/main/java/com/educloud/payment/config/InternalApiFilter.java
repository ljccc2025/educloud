package com.educloud.payment.config;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class InternalApiFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = InternalApiFilter.class.getName() + ".clientId";

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final PaymentProperties paymentProperties;

    public InternalApiFilter(ObjectProvider<JwtDecoder> jwtDecoderProvider, PaymentProperties paymentProperties) {
        this.jwtDecoderProvider = Objects.requireNonNull(jwtDecoderProvider, "jwtDecoderProvider");
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
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
            String configuredSecret = paymentProperties.internal() != null ? paymentProperties.internal().secretToken() : null;
            // 常量时间比较，避免时序侧信道枚举密钥
            if (configuredSecret != null && !configuredSecret.isBlank()
                    && MessageDigest.isEqual(
                            configuredSecret.getBytes(StandardCharsets.UTF_8),
                            internalTokenHeader.getBytes(StandardCharsets.UTF_8))) {
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

        Jwt token;
        try {
            token = jwtDecoder.decode(authorization.substring(7).trim());
        } catch (JwtException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Object clientId = token.getClaim("clientId");
        String effectiveAudience = paymentProperties.internal() != null
                ? paymentProperties.internal().effectiveInternalAudience()
                : "educloud-payment";
        if (!(clientId instanceof String clientIdText)
                || !token.getAudience().contains(effectiveAudience)
                || (paymentProperties.internal() != null
                    && !paymentProperties.internal().effectiveAllowedClientIds().isEmpty()
                    && !paymentProperties.internal().effectiveAllowedClientIds().contains(clientIdText))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        request.setAttribute(CLIENT_ID_ATTRIBUTE, clientIdText);
        filterChain.doFilter(request, response);
    }
}
