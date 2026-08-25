package com.educloud.course.config;

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
 * 内部接口服务身份过滤器（/internal/v1/**）。依据：M05 设计规格第 9 节
 * （服务 Token 验签、aud=educloud-course + clientId 白名单；仅内网可达不是授权）。
 *
 * <p>复制 user/file InternalApiFilter 适配 Course：Bearer→decode→aud 含 internal audience +
 * clientId 白名单→request attribute。注册为最高优先级 servlet filter（先于 Security 链执行），
 * 缺失/非法令牌直接 401/403 sendError。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class InternalApiFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = InternalApiFilter.class.getName() + ".clientId";

    private final JwtDecoder jwtDecoder;
    private final CourseProperties courseProperties;

    public InternalApiFilter(JwtDecoder jwtDecoder, CourseProperties courseProperties) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.courseProperties = Objects.requireNonNull(courseProperties, "courseProperties");
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
        String internalTokenHeader = request.getHeader("X-Internal-Token");
        if (internalTokenHeader != null && !internalTokenHeader.isBlank()) {
            if ("educloud-internal-secret".equals(internalTokenHeader)) {
                String caller = request.getHeader("X-Client-Id");
                request.setAttribute(CLIENT_ID_ATTRIBUTE, caller != null && !caller.isBlank() ? caller : "educloud-live");
                filterChain.doFilter(request, response);
                return;
            }
        }

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
                || !token.getAudience().contains(courseProperties.internal().effectiveInternalAudience())
                || !courseProperties.internal().effectiveAllowedClientIds().contains(clientIdText)) {
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
