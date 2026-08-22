package com.educloud.file.security;

import com.educloud.common.error.CommonErrorCode;
import com.educloud.file.config.FileProperties;
import com.educloud.file.exception.FileErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * storage-tests 限频过滤器：POST /api/v1/files/storage-tests 按用户（JWT sub，无则 IP）
 * Redis 计数，rate-limit 次/window，超限 429 写 {@link FileErrorCode#STORAGE_TEST_RATE_LIMITED}
 * 信封；Redis 异常失败关闭 503（复用 {@link CommonErrorCode#DEPENDENCY_UNAVAILABLE}）。
 *
 * <p>参考 user RegistrationRateLimitFilter 的 exceeded 模式；不作为 @Component 注册，
 * 由同文件内 @Configuration 在完整应用上下文（含 Redis/配置属性）创建，避免
 * @WebMvcTest 等切片因缺 Bean 失败。注册为最高优先级 servlet filter（先于 Security 链），
 * 从 Authorization 头自行解码取 sub——未认证/非法令牌回退 IP 计数。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class StorageTestRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageTestRateLimitFilter.class);
    private static final String STORAGE_TEST_PATH = "/api/v1/files/storage-tests";
    private static final String REDIS_KEY_PREFIX = "educloud:file:ratelimit:storage-test";

    private final JwtDecoder jwtDecoder;
    private final StringRedisTemplate redis;
    private final FileProperties properties;
    private final ObjectMapper objectMapper;

    public StorageTestRateLimitFilter(
            JwtDecoder jwtDecoder,
            StringRedisTemplate redis,
            FileProperties properties,
            ObjectMapper objectMapper) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !STORAGE_TEST_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = userSubject(request);
        try {
            String key = REDIS_KEY_PREFIX + ":" + subject;
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                // Redis 返回空值视为依赖异常：失败关闭（503），防止绕过限流。
                throw new IllegalStateException("Redis increment returned null for storage test rate limit");
            }
            if (count == 1L) {
                redis.expire(key, properties.storageTest().window());
            }
            if (count > properties.storageTest().rateLimit()) {
                writeError(response, FileErrorCode.STORAGE_TEST_RATE_LIMITED, 429, retryAfter());
                return;
            }
        } catch (Exception failure) {
            LOGGER.error("Storage test rate limit check failed; rejecting with 503", failure);
            writeError(response, CommonErrorCode.DEPENDENCY_UNAVAILABLE, 503, null);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 用户维度：优先 JWT sub；缺失/非法时回退客户端 IP。 */
    private String userSubject(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                Jwt token = jwtDecoder.decode(authorization.substring(7).trim());
                String subject = token.getSubject();
                if (subject != null && !subject.isBlank()) {
                    return "user:" + subject;
                }
            } catch (JwtException ignored) {
                // 非法 token 由 Security 链返回 401；此处回退 IP 维度计数。
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    private long retryAfter() {
        return Math.max(1L, properties.storageTest().window().toSeconds());
    }

    private void writeError(HttpServletResponse response, com.educloud.common.error.ErrorCode code,
                            int status, Long retryAfter) throws IOException {
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
     * 切片测试不会加载此类（与 user RegistrationRateLimitFilter 同构）。
     */
    @Configuration(proxyBeanMethods = false)
    static class StorageTestRateLimitConfiguration {

        @Bean
        StorageTestRateLimitFilter storageTestRateLimitFilter(
                JwtDecoder jwtDecoder,
                StringRedisTemplate redis,
                FileProperties properties,
                ObjectMapper objectMapper) {
            return new StorageTestRateLimitFilter(jwtDecoder, redis, properties, objectMapper);
        }
    }
}
