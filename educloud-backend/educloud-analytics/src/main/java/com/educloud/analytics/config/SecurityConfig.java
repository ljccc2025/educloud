package com.educloud.analytics.config;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AnalyticsProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            ApiResponseFactory responses,
            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.HEAD,
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().permitAll()) // Permit all at filter level to allow gateway token passthrough & controller annotations
                .oauth2ResourceServer(oauth2 -> {
                    oauth2.jwt(jwt -> {
                        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
                        if (decoder != null) {
                            jwt.decoder(decoder);
                        }
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter());
                    });
                    oauth2.authenticationEntryPoint(authenticationEntryPoint(responses, objectMapper));
                    oauth2.accessDeniedHandler(accessDeniedHandler(responses, objectMapper));
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint(responses, objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(responses, objectMapper)));
        return http.build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new AnalyticsJwtGrantedAuthoritiesConverter());
        return converter;
    }

    private static class AnalyticsJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();

            Object permissions = jwt.getClaim("permissions");
            if (permissions instanceof Collection<?> coll) {
                for (Object item : coll) {
                    if (item instanceof String auth && !auth.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(auth));
                    }
                }
            }

            Object authClaim = jwt.getClaim("authorities");
            if (authClaim instanceof Collection<?> coll) {
                for (Object item : coll) {
                    if (item instanceof String auth && !auth.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(auth));
                    }
                }
            }

            Object roles = jwt.getClaim("roles");
            if (roles instanceof Collection<?> coll) {
                for (Object item : coll) {
                    if (item instanceof String role && !role.isBlank()) {
                        String trimmed = role.trim();
                        if (!trimmed.startsWith("ROLE_")) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
                        }
                        authorities.add(new SimpleGrantedAuthority(trimmed));
                        if ("SYSTEM_ADMIN".equalsIgnoreCase(trimmed) || "ROLE_SYSTEM_ADMIN".equalsIgnoreCase(trimmed)) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                            authorities.add(new SimpleGrantedAuthority("ADMIN"));
                        }
                    }
                }
            }

            Object userType = jwt.getClaim("userType");
            if (userType instanceof String ut && !ut.isBlank()) {
                String trimmed = ut.trim();
                if ("ADMIN".equalsIgnoreCase(trimmed)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    authorities.add(new SimpleGrantedAuthority("ADMIN"));
                }
            }

            Collection<GrantedAuthority> defaultAuths = defaultConverter.convert(jwt);
            if (defaultAuths != null) {
                authorities.addAll(defaultAuths);
            }

            return Collections.unmodifiableSet(authorities);
        }
    }

    private AuthenticationEntryPoint authenticationEntryPoint(
            ApiResponseFactory responses,
            ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            log.warn("Analytics API authentication failed on URI [{}]: {}", request.getRequestURI(), authException.getMessage());
            ApiResponse<Void> body = responses.error(
                    CommonErrorCode.UNAUTHENTICATED,
                    "Authentication required",
                    null);
            response.setStatus(CommonErrorCode.UNAUTHENTICATED.httpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }

    private static AccessDeniedHandler accessDeniedHandler(
            ApiResponseFactory responses, ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            ApiResponse<Void> body = responses.error(
                    CommonErrorCode.ACCESS_DENIED,
                    CommonErrorCode.ACCESS_DENIED.defaultMessage(),
                    null);
            response.setStatus(CommonErrorCode.ACCESS_DENIED.httpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }
}
