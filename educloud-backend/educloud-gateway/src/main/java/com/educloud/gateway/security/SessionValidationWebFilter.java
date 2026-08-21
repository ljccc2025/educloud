package com.educloud.gateway.security;

import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.route.AccessDecision;
import com.educloud.gateway.route.AccessPolicy;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.util.Objects;

public final class SessionValidationWebFilter implements WebFilter {

    private final AccessPolicy accessPolicy;
    private final SessionVerifier sessionVerifier;
    private final GatewayErrorWriter errorWriter;

    public SessionValidationWebFilter(
            AccessPolicy accessPolicy,
            SessionVerifier sessionVerifier,
            GatewayErrorWriter errorWriter) {
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.sessionVerifier = Objects.requireNonNull(sessionVerifier, "sessionVerifier");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AccessDecision access = accessPolicy.classify(
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().pathWithinApplication());
        exchange.getAttributes().put(GatewayExchangeAttributes.ACCESS_DECISION, access);

        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(authentication -> verifyAndContinue(exchange, chain, authentication)
                        .thenReturn(Boolean.TRUE))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(Boolean.TRUE)))
                .then();
    }

    private Mono<Void> verifyAndContinue(
            ServerWebExchange exchange,
            WebFilterChain chain,
            JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        String subject = jwt.getSubject();
        String sessionId = stringClaim(jwt, "sid");
        Long tokenVersion = integerClaim(jwt, "tokenVersion");
        if (subject == null || subject.isBlank()
                || sessionId == null || sessionId.isBlank()
                || tokenVersion == null || tokenVersion < 0) {
            return errorWriter.write(exchange, GatewayFailure.of(GatewayErrorCode.UNAUTHENTICATED));
        }

        Mono<SessionCheckResult> sessionCheck = Mono.defer(
                        () -> sessionVerifier.verify(subject, sessionId, tokenVersion))
                .onErrorReturn(SessionCheckResult.DEPENDENCY_ERROR);
        return sessionCheck
                .flatMap(result -> switch (result) {
                    case ACTIVE -> chain.filter(exchange);
                    case MISSING, REVOKED, SUBJECT_MISMATCH, VERSION_MISMATCH ->
                            errorWriter.write(exchange, GatewayFailure.of(GatewayErrorCode.UNAUTHENTICATED));
                    case CORRUPT, DEPENDENCY_ERROR ->
                            errorWriter.write(exchange,
                                    GatewayFailure.of(GatewayErrorCode.DEPENDENCY_UNAVAILABLE));
                });
    }

    private static String stringClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value instanceof String string ? string : null;
    }

    private static Long integerClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }
}
