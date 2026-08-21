package com.educloud.gateway.security;

import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.route.AccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionValidationWebFilterTest {

    @Test
    void requestsWithoutAnAuthenticatedBearerContinueWithoutRedis() {
        for (RequestCase request : List.of(
                new RequestCase(HttpMethod.GET, "/api/v1/courses"),
                new RequestCase(HttpMethod.POST, "/api/v1/auth/login"),
                new RequestCase(HttpMethod.POST, "/api/v1/payment-callbacks/alipay/notify"),
                new RequestCase(HttpMethod.GET, "/api/v1/users/me"))) {
            SessionVerifier sessions = mock(SessionVerifier.class);
            GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
            WebFilterChain chain = continuingChain();

            filter(sessions, writer).filter(exchange(request), chain).block();

            verify(chain).filter(any());
            verifyNoInteractions(sessions, writer);
        }
    }

    @Test
    void activeSessionsContinueForPublicAndProtectedBearerRequests() {
        for (String path : List.of("/api/v1/courses", "/api/v1/users/me")) {
            SessionVerifier sessions = mock(SessionVerifier.class);
            when(sessions.verify("user-123", "session-123", 7L))
                    .thenReturn(Mono.just(SessionCheckResult.ACTIVE));
            GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
            WebFilterChain chain = continuingChain();

            filter(sessions, writer).filter(
                            exchange(new RequestCase(HttpMethod.GET, path)), chain)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication()))
                    .block();

            verify(sessions).verify("user-123", "session-123", 7L);
            verify(chain).filter(any());
            verifyNoInteractions(writer);
        }
    }

    @Test
    void invalidAuthoritativeSessionStatesReturn401WithoutCallingDownstream() {
        for (SessionCheckResult result : List.of(
                SessionCheckResult.MISSING,
                SessionCheckResult.REVOKED,
                SessionCheckResult.SUBJECT_MISMATCH,
                SessionCheckResult.VERSION_MISMATCH)) {
            assertRejected(result, GatewayErrorCode.UNAUTHENTICATED);
        }
    }

    @Test
    void corruptOrUnavailableSessionStateReturns503WithoutCallingDownstream() {
        assertRejected(SessionCheckResult.CORRUPT, GatewayErrorCode.DEPENDENCY_UNAVAILABLE);
        assertRejected(SessionCheckResult.DEPENDENCY_ERROR, GatewayErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void malformedAuthenticatedClaimsFailClosedWithoutCallingRedis() {
        SessionVerifier sessions = mock(SessionVerifier.class);
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);
        JwtAuthenticationToken malformed = authentication(null, "session-123", 7L);

        filter(sessions, writer).filter(
                        exchange(new RequestCase(HttpMethod.GET, "/api/v1/users/me")), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(malformed))
                .block();

        verifyFailure(writer, GatewayErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(sessions, chain);
    }

    @Test
    void unexpectedVerifierErrorsFailClosedAsDependencyUnavailable() {
        SessionVerifier sessions = mock(SessionVerifier.class);
        when(sessions.verify(any(), any(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("redis detail")));
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);

        filter(sessions, writer).filter(
                        exchange(new RequestCase(HttpMethod.GET, "/api/v1/users/me")), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication()))
                .block();

        verifyFailure(writer, GatewayErrorCode.DEPENDENCY_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }

    private static void assertRejected(SessionCheckResult result, GatewayErrorCode expectedCode) {
        SessionVerifier sessions = mock(SessionVerifier.class);
        when(sessions.verify("user-123", "session-123", 7L)).thenReturn(Mono.just(result));
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);

        filter(sessions, writer).filter(
                        exchange(new RequestCase(HttpMethod.GET, "/api/v1/users/me")), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication()))
                .block();

        verifyFailure(writer, expectedCode);
        verify(chain, never()).filter(any());
    }

    private static void verifyFailure(GatewayErrorWriter writer, GatewayErrorCode expectedCode) {
        verify(writer).write(any(), org.mockito.ArgumentMatchers.argThat(
                (GatewayFailure failure) -> failure.code() == expectedCode));
    }

    private static SessionValidationWebFilter filter(
            SessionVerifier sessions, GatewayErrorWriter writer) {
        return new SessionValidationWebFilter(AccessPolicy.standard(), sessions, writer);
    }

    private static WebFilterChain continuingChain() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    private static JwtAuthenticationToken authentication() {
        return authentication("user-123", "session-123", 7L);
    }

    private static JwtAuthenticationToken authentication(
            String subject, String sessionId, Object tokenVersion) {
        Jwt.Builder builder = Jwt.withTokenValue("runtime-test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.parse("2026-08-20T11:00:00Z"))
                .expiresAt(Instant.parse("2026-08-20T13:00:00Z"))
                .claim("sid", sessionId)
                .claim("tokenVersion", tokenVersion);
        if (subject != null) {
            builder.subject(subject);
        }
        return new JwtAuthenticationToken(builder.build());
    }

    private static MockServerWebExchange exchange(RequestCase request) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.method(request.method(), request.path()).build());
    }

    private record RequestCase(HttpMethod method, String path) {
    }
}
