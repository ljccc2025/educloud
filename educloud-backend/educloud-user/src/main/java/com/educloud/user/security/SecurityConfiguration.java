package com.educloud.user.security;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * User 服务安全配置：Resource Server（本地公钥验签）+ 方法安全。
 * 依据：M03 设计规格第 12 节（各业务服务再次校验 Token）与 API 规范（认证端点匿名访问）。
 * Gateway 已做 Redis 会话在线撤销；服务端按设计只验签并读取 claims，不重复检查 Redis。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            ApiResponseFactory responses,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/platform-config/public",
                                "/actuator/health/**").permitAll()
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
     * 依据：M03 设计规格第 6 节（方法级 @PreAuthorize + 权限码）。
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

    @Bean
    public JwtDecoder jwtDecoder(JwtKeyProvider keyProvider, JwtProperties jwtProperties) {
        // Spring Security 6.2 无 withJwkSource 静态方法：用 Nimbus jwt.proc 处理器 +
        // JWSVerificationKeySelector 消费本地 JWKSource（官方文档推荐方式，支持多 kid 轮换）。
        com.nimbusds.jwt.proc.ConfigurableJWTProcessor<com.nimbusds.jose.proc.SecurityContext> processor =
                new com.nimbusds.jwt.proc.DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new com.nimbusds.jose.proc.JWSVerificationKeySelector(
                com.nimbusds.jose.JWSAlgorithm.RS256, keyProvider.jwkSource()));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()));
        return decoder;
    }

    private static AuthenticationEntryPoint authenticationEntryPoint(
            ApiResponseFactory responses, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
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