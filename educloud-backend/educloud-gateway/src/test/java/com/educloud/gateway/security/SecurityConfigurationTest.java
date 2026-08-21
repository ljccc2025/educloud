package com.educloud.gateway.security;

import com.educloud.gateway.error.GatewayAccessDeniedHandler;
import com.educloud.gateway.error.GatewayAuthenticationEntryPoint;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.route.AccessDecision;
import com.educloud.gateway.route.AccessKind;
import com.educloud.gateway.route.AccessPolicy;
import com.educloud.gateway.route.InternalPathWebFilter;
import com.educloud.gateway.route.RouteGroups;
import com.educloud.gateway.web.RequestIdWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authorization.AuthorizationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void publicMatcherDelegatesToTheSingleAccessPolicy() {
        AccessPolicy policy = mock(AccessPolicy.class);
        when(policy.classify(any(), any())).thenReturn(
                new AccessDecision(AccessKind.PUBLIC_READ, RouteGroups.CATALOG));
        ServerWebExchangeMatcher matcher = SecurityConfiguration.publicAndCallbackMatcher(policy);
        var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/one-source"));

        assertThat(matcher.matches(exchange).block().isMatch()).isTrue();
        verify(policy).classify(HttpMethod.GET, PathContainer.parsePath("/one-source"));
    }

    @Test
    void sessionValidationRunsAfterBearerAuthenticationAndBeforeAuthorization() {
        Fixture fixture = fixture(token -> Mono.error(new JwtException("unused")), SessionCheckResult.ACTIVE);
        List<WebFilter> filters = fixture.securityChain().getWebFilters().collectList().block();

        int authentication = indexOf(filters, AuthenticationWebFilter.class);
        int session = indexOf(filters, SessionValidationWebFilter.class);
        int authorization = indexOf(filters, AuthorizationWebFilter.class);

        assertThat(authentication).isGreaterThanOrEqualTo(0);
        assertThat(session).isGreaterThan(authentication);
        assertThat(session).isLessThan(authorization);
    }

    @Test
    void enforcesTheOptionalBearerAndProtectedRouteMatrix() {
        ReactiveJwtDecoder decoder = token -> "valid-token".equals(token)
                ? Mono.just(jwt())
                : Mono.error(new BadJwtException("invalid token detail"));
        Fixture fixture = fixture(decoder, SessionCheckResult.ACTIVE);

        fixture.client().get().uri("/api/v1/courses")
                .exchange().expectStatus().isNoContent();
        fixture.client().post().uri("/api/v1/auth/login")
                .exchange().expectStatus().isNoContent();
        fixture.client().post().uri("/api/v1/payment-callbacks/alipay/notify")
                .exchange().expectStatus().isNoContent();
        fixture.client().get().uri("/api/v1/courses")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
        fixture.client().get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
        fixture.client().get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer valid-token")
                .exchange().expectStatus().isNoContent();
        fixture.client().get().uri("/internal/v1/admin")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GATEWAY_ROUTE_NOT_FOUND");

        assertThat(fixture.downstreamCalls()).hasValue(4);
        verify(fixture.sessions()).verify("user-123", "session-123", 7L);
    }

    @Test
    void mapsARejectedAuthoritativeSessionTo401AndDependencyFailureTo503() {
        for (SessionCase sessionCase : List.of(
                new SessionCase(SessionCheckResult.REVOKED, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"),
                new SessionCase(SessionCheckResult.DEPENDENCY_ERROR,
                        HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE"))) {
            Fixture fixture = fixture(token -> Mono.just(jwt()), sessionCase.result());

            fixture.client().get().uri("/api/v1/users/me")
                    .header("Authorization", "Bearer valid-token")
                    .exchange()
                    .expectStatus().isEqualTo(sessionCase.status())
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(sessionCase.code());

            assertThat(fixture.downstreamCalls()).hasValue(0);
        }
    }

    private static Fixture fixture(ReactiveJwtDecoder decoder, SessionCheckResult sessionResult) {
        SessionVerifier sessions = mock(SessionVerifier.class);
        when(sessions.verify(any(), any(), anyLong())).thenReturn(Mono.just(sessionResult));
        AccessPolicy accessPolicy = AccessPolicy.standard();
        GatewayErrorWriter writer = new GatewayErrorWriter(objectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("gatewayMetrics", GatewayMetrics.noOp());
        GatewayAuthenticationEntryPoint entryPoint = new GatewayAuthenticationEntryPoint(
                writer, beans.getBeanProvider(GatewayMetrics.class));
        GatewayAccessDeniedHandler deniedHandler = new GatewayAccessDeniedHandler(
                writer, beans.getBeanProvider(GatewayMetrics.class));
        SecurityWebFilterChain securityChain = new SecurityConfiguration().gatewaySecurityFilterChain(
                ServerHttpSecurity.http(), decoder, accessPolicy, sessions, writer, entryPoint, deniedHandler);
        AtomicInteger downstreamCalls = new AtomicInteger();
        WebHandler downstream = exchange -> {
            downstreamCalls.incrementAndGet();
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return exchange.getResponse().setComplete();
        };
        WebTestClient client = WebTestClient.bindToWebHandler(downstream)
                .webFilter(
                        new RequestIdWebFilter(),
                        new InternalPathWebFilter(accessPolicy, writer),
                        new WebFilterChainProxy(securityChain))
                .build();
        return new Fixture(securityChain, client, sessions, downstreamCalls);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("sid", "session-123")
                .claim("tokenVersion", 7L)
                .issuedAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(300))
                .build();
    }

    private static int indexOf(List<WebFilter> filters, Class<?> type) {
        for (int index = 0; index < filters.size(); index++) {
            if (type.isInstance(filters.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private record Fixture(
            SecurityWebFilterChain securityChain,
            WebTestClient client,
            SessionVerifier sessions,
            AtomicInteger downstreamCalls) {
    }

    private record SessionCase(SessionCheckResult result, HttpStatus status, String code) {
    }
}
