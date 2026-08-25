package com.educloud.notification.config;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(NotificationProperties.class)
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
                        .requestMatchers(HttpMethod.GET, "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
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
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }
}
