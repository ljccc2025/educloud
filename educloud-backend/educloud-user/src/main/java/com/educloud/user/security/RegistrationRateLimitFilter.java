package com.educloud.user.security;

import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.config.RegistrationRateLimitProperties;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.session.SessionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册限流过滤器：POST /api/v1/auth/register 按 IP + 设备双层 Redis 计数，超限 429；
 * Redis 不可用失败关闭 503。依据：M03 注册限流设计 4 节。
 * 注意：不在类上标注 @Component，避免 @WebMvcTest 切片把该过滤器当作 Filter 组件加载后因缺少
 * Redis/配置属性 Bean 而失败；改为通过同文件内的 @Configuration 注册，完整应用上下文才会创建该 Bean。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class RegistrationRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationRateLimitFilter.class);
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private final StringRedisTemplate redis;
    private final RegistrationRateLimitProperties properties;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;

    public RegistrationRateLimitFilter(
            StringRedisTemplate redis,
            RegistrationRateLimitProperties properties,
            SessionProperties sessionProperties,
            ObjectMapper objectMapper) {
        this.redis = redis;
        this.properties = properties;
        this.sessionProperties = sessionProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !REGISTER_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String deviceHeader = request.getHeader("X-Device-Id");
            if (deviceHeader != null && !deviceHeader.isBlank()) {
                if (exceeded("register-device", SessionFactory.sha256Hex(deviceHeader.trim()),
                        properties.deviceMaxAttempts(), response)) {
                    return;
                }
            }
            if (exceeded("register-ip", SessionFactory.sha256Hex(request.getRemoteAddr()),
                    properties.ipMaxAttempts(), response)) {
                return;
            }
        } catch (Exception failure) {
            // Redis 不可用：失败关闭（拒绝注册防绕过），并记录原因。
            LOGGER.error("Registration rate limit check failed; rejecting with 503", failure);
            writeError(response, CommonErrorCode.DEPENDENCY_UNAVAILABLE, 503, null);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exceeded(String subKey, String hashedValue, int maxAttempts, HttpServletResponse response)
            throws IOException {
        String key = properties.redisKeyPrefix().replace("{env}", sessionProperties.environment())
                + ":" + subKey + ":" + hashedValue;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, properties.window());
        }
        if (count != null && count > maxAttempts) {
            writeError(response, CommonErrorCode.RATE_LIMITED, 429, retryAfter());
            return true;
        }
        return false;
    }

    private long retryAfter() {
        return Math.max(1L, properties.window().toSeconds());
    }

    private void writeError(HttpServletResponse response, CommonErrorCode code, int status, Long retryAfter)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.code());
        body.put("message", code.defaultMessage());
        body.put("data", null);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (retryAfter != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * 过滤器注册配置：仅完整应用上下文（含 Redis 与配置属性）才创建该过滤器，
     * @WebMvcTest 等切片测试不会加载此类，避免切片上下文缺 Bean 失败。
     */
    @Configuration(proxyBeanMethods = false)
    static class RegistrationRateLimitConfiguration {

        @Bean
        RegistrationRateLimitFilter registrationRateLimitFilter(
                StringRedisTemplate redis,
                RegistrationRateLimitProperties properties,
                SessionProperties sessionProperties,
                ObjectMapper objectMapper) {
            return new RegistrationRateLimitFilter(redis, properties, sessionProperties, objectMapper);
        }
    }
}
