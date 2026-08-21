package com.educloud.gateway.security;

import com.educloud.gateway.error.GatewayAccessDeniedHandler;
import com.educloud.gateway.error.GatewayAuthenticationEntryPoint;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.route.AccessDecision;
import com.educloud.gateway.route.AccessPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

import java.util.Objects;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder gatewayJwtDecoder,
            AccessPolicy accessPolicy,
            SessionVerifier sessionVerifier,
            GatewayErrorWriter errorWriter,
            GatewayAuthenticationEntryPoint authenticationEntryPoint,
            GatewayAccessDeniedHandler accessDeniedHandler) {
        SessionValidationWebFilter sessionValidationWebFilter = new SessionValidationWebFilter(
                accessPolicy, sessionVerifier, errorWriter);
        ServerWebExchangeMatcher publicAndCallbackMatcher = publicAndCallbackMatcher(accessPolicy);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/internal/v1/**").denyAll()
                        .matchers(publicAndCallbackMatcher).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(gatewayJwtDecoder))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(sessionValidationWebFilter, SecurityWebFiltersOrder.AUTHORIZATION)
                .build();
    }

    static ServerWebExchangeMatcher publicAndCallbackMatcher(AccessPolicy accessPolicy) {
        Objects.requireNonNull(accessPolicy, "accessPolicy");
        return exchange -> {
            HttpMethod method = exchange.getRequest().getMethod();
            AccessDecision decision = accessPolicy.classify(
                    method, exchange.getRequest().getPath().pathWithinApplication());
            return decision.mayProceedWithoutBearer()
                    ? ServerWebExchangeMatcher.MatchResult.match()
                    : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }
}
