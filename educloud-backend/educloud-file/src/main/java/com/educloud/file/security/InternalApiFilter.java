package com.educloud.file.security;

import com.educloud.file.config.FileProperties;
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

/**
 * 内部接口服务身份过滤器（/internal/v1/**）。依据：M04 设计规格第 9 节
 * （服务 Token 验签、aud=educloud-file + clientId 白名单；仅内网可达不是授权）。
 *
 * <p>复制 user InternalApiFilter 适配 File：Bearer→decode→aud 含 internal audience +
 * clientId 白名单→request attribute。注册为最高优先级 servlet filter（先于
 * Security 链执行），缺失/非法令牌直接 401/403 sendError。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class InternalApiFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = InternalApiFilter.class.getName() + ".clientId";

    private final JwtDecoder jwtDecoder;
    private final FileProperties fileProperties;

    public InternalApiFilter(JwtDecoder jwtDecoder, FileProperties fileProperties) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.fileProperties = Objects.requireNonNull(fileProperties, "fileProperties");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 用 servletPath 而非 requestURI：context-path/网关 rewrite 场景下
        // requestURI 可能带前缀，servletPath 才是被映射的 servlet 内路径。
        return !request.getServletPath().startsWith("/internal/v1/");
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
                || !token.getAudience().contains(fileProperties.internal().effectiveInternalAudience())
                || !fileProperties.internal().allowedClientIds().contains(clientIdText)) {
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
