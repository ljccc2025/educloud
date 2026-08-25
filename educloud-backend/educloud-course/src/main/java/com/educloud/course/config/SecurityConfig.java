package com.educloud.course.config;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Course 服务安全配置：Resource Server（User 公钥 JWKS 验签）+ 方法安全。
 *
 * <p>依据：M05 设计规格第 9 节（对外 API 经 Gateway，Bearer + course:* 权限码；Course
 * 再次验签）。全部请求 authenticated，permitAll 仅 GET /actuator/health/**、公开分类
 * GET /api/v1/categories（M05 任务 7）与公开目录 GET /api/v1/courses、
 * GET /api/v1/courses/{courseId}（M05 任务 11：Gateway AccessPolicy.PUBLIC_READ 匿名
 * 放行后转发不带 Authorization 头，服务内必须同步放行，参照 user
 * /api/v1/platform-config/public 处理；方法限定 GET 与 HEAD（两条 requestMatchers
 * 链式注册，Spring MVC 自动把 HEAD 映射到 GET 处理器）与网关 PUBLIC_READ 仅
 * GET/HEAD 的契约对齐。
 * 路径变量用 {@code {courseId:[0-9]+}} 限定数字：任务 11 的目录详情
 * courseId 为雪花数字，而测试探针 /api/v1/courses/security-probe 必须保持受保护
 * （SecurityConfigTest 无 token 401 断言），若用 {@code *} 会误放行探针端点。
 * /internal/v1/** 由 {@link InternalApiFilter} 在 Security 链之前处理，服务令牌同样
 * 能通过 Resource Server 解码（CourseJwtValidator 宽松分支），故无需单独放行。权限码
 * （permissions claim）无前缀映射为 authority（course:create 等 9 个，见
 * CoursePermissions），驱动控制器 @PreAuthorize。401 统一返回 ApiResponse 信封。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            ApiResponseFactory responses,
            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/api/v1/categories",
                                "/api/v1/courses", "/api/v1/courses/{courseId:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health/**", "/api/v1/categories",
                                "/api/v1/courses", "/api/v1/courses/{courseId:[0-9]+}").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint(responses, objectMapper)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint(responses, objectMapper)));
        return http.build();
    }

    /**
     * JWT permissions claim -> GrantedAuthority（无前缀，权限码直接作为 authority）。
     * 依据：M05 设计规格第 6 节（course:create / course:audit 等权限码）。
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("permissions");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static AuthenticationEntryPoint authenticationEntryPoint(
            ApiResponseFactory responses, ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            ApiResponse<Void> body = responses.error(
                    CommonErrorCode.UNAUTHENTICATED,
                    CommonErrorCode.UNAUTHENTICATED.defaultMessage(),
                    null);
            response.setStatus(CommonErrorCode.UNAUTHENTICATED.httpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }
}