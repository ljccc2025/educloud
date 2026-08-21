package com.educloud.gateway.error;

import com.educloud.common.web.RequestContext;
import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewayErrorWriterTest {

    private static final String REQUEST_ID = "request-123";
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void writesTheCompleteFixedErrorTableAsCommonApiResponses() throws Exception {
        for (GatewayErrorCode code : GatewayErrorCode.values()) {
            MockServerWebExchange exchange = exchange();

            writer().write(exchange, GatewayFailure.of(code)).block();

            JsonNode body = OBJECT_MAPPER.readTree(exchange.getResponse().getBodyAsString().block());
            assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(code.httpStatus());
            assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(exchange.getResponse().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER))
                    .isEqualTo(REQUEST_ID);
            assertThat(body.get("code").asText()).isEqualTo(code.code());
            assertThat(body.get("message").asText()).isEqualTo(code.defaultMessage());
            assertThat(body.get("data").isNull()).isTrue();
            assertThat(body.get("requestId").asText()).isEqualTo(REQUEST_ID);
            assertThat(body.get("timestamp").asText()).isEqualTo(NOW.toString());
        }
    }

    @Test
    void writesARoundedUpRetryAfterForRateLimits() {
        MockServerWebExchange exchange = exchange();

        writer().write(exchange, GatewayFailure.rateLimited(Duration.ofMillis(1500))).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(429);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
    }

    @Test
    void neverCopiesSensitiveRequestOrFailureDetailsIntoTheBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("http://internal.redis.local:6379/educloud/session/sensitive-sid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sensitive-token")
                .header(HttpHeaders.COOKIE, "refresh=credential")
                .header("X-Claims", "admin-secret-claim")
                .build());
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);
        exchange.getAttributes().put("redisKey", "educloud:session:sensitive-sid");

        writer().write(exchange, GatewayFailure.of(GatewayErrorCode.INTERNAL_ERROR)).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body)
                .doesNotContain("BadCredentialsException")
                .doesNotContain("internal.redis.local")
                .doesNotContain("educloud:session")
                .doesNotContain("sensitive-token")
                .doesNotContain("admin-secret-claim")
                .doesNotContain("credential");
    }

    @Test
    void leavesAnAlreadyCommittedResponseUntouched() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().setComplete().block();

        writer().write(exchange, GatewayFailure.of(GatewayErrorCode.INTERNAL_ERROR)).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block()).isEmpty();
    }

    @Test
    void completesWithoutAnUnsafeFallbackBodyWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("sensitive serialization detail") { })
                .when(failingMapper).writeValueAsBytes(any());
        MockServerWebExchange exchange = exchange();

        new GatewayErrorWriter(failingMapper, Clock.fixed(NOW, ZoneOffset.UTC))
                .write(exchange, GatewayFailure.of(GatewayErrorCode.INTERNAL_ERROR))
                .block();

        assertThat(exchange.getResponse().isCommitted()).isTrue();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(500);
        assertThat(exchange.getResponse().getBodyAsString().block()).isEmpty();
    }

    @Test
    void securityHandlersUseFixedResponsesAndLowCardinalityMetrics() {
        GatewayMetrics metrics = mock(GatewayMetrics.class);
        GatewayErrorWriter writer = writer();

        MockServerWebExchange unauthenticated = exchange();
        new GatewayAuthenticationEntryPoint(writer, metrics)
                .commence(unauthenticated, new BadCredentialsException("Bearer secret"))
                .block();
        assertThat(unauthenticated.getResponse().getStatusCode().value()).isEqualTo(401);
        verify(metrics).recordSecurityFailure(
                GatewayMetrics.SecurityFailureCategory.AUTHENTICATION, "unmatched");

        MockServerWebExchange denied = exchange();
        new GatewayAccessDeniedHandler(writer, metrics)
                .handle(denied, new AccessDeniedException("claims secret"))
                .block();
        assertThat(denied.getResponse().getStatusCode().value()).isEqualTo(403);
        verify(metrics).recordSecurityFailure(
                GatewayMetrics.SecurityFailureCategory.AUTHORIZATION, "unmatched");
    }

    @Test
    void exposesOnlyTheApprovedPublicMessages() {
        assertThat(GatewayErrorCode.GATEWAY_BAD_REQUEST.defaultMessage()).isEqualTo("Bad gateway request");
        assertThat(GatewayErrorCode.UNAUTHENTICATED.defaultMessage()).isEqualTo("Authentication required");
        assertThat(GatewayErrorCode.ACCESS_DENIED.defaultMessage()).isEqualTo("Access denied");
        assertThat(GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND.defaultMessage()).isEqualTo("Route not found");
        assertThat(GatewayErrorCode.GATEWAY_REQUEST_TOO_LARGE.defaultMessage()).isEqualTo("Request is too large");
        assertThat(GatewayErrorCode.GATEWAY_UNSUPPORTED_MEDIA_TYPE.defaultMessage()).isEqualTo("Unsupported media type");
        assertThat(GatewayErrorCode.RATE_LIMITED.defaultMessage()).isEqualTo("Too many requests");
        assertThat(GatewayErrorCode.DEPENDENCY_UNAVAILABLE.defaultMessage()).isEqualTo("Dependency unavailable");
        assertThat(GatewayErrorCode.GATEWAY_TIMEOUT.defaultMessage()).isEqualTo("Gateway timeout");
        assertThat(GatewayErrorCode.INTERNAL_ERROR.defaultMessage()).isEqualTo("Internal server error");
    }

    private static GatewayErrorWriter writer() {
        return new GatewayErrorWriter(OBJECT_MAPPER, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MockServerWebExchange exchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me"));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);
        return exchange;
    }
}
