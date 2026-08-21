package com.educloud.gateway.web;

import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdWebFilterTest {

    private static final UUID GENERATED_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void preservesAValidRequestIdAcrossExchangeRequestResponseContextAndJson() throws Exception {
        String accepted = "client.request_01-ABC";
        MockServerWebExchange exchange = exchangeWithRequestId(accepted);
        RequestIdWebFilter filter = filter();
        GatewayErrorWriter writer = writer();
        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        AtomicReference<String> reactorRequestId = new AtomicReference<>();
        AtomicReference<String> existingTraceId = new AtomicReference<>();

        filter.filter(exchange, filtered -> Mono.deferContextual(context -> {
                    downstreamHeader.set(filtered.getRequest().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER));
                    reactorRequestId.set(context.get(GatewayExchangeAttributes.REACTOR_CONTEXT_REQUEST_ID));
                    existingTraceId.set(context.get("traceId"));
                    return writer.write(filtered, GatewayFailure.of(GatewayErrorCode.GATEWAY_BAD_REQUEST));
                }))
                .contextWrite(Context.of("traceId", "trace-existing"))
                .block();

        JsonNode body = OBJECT_MAPPER.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat((String) exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID)).isEqualTo(accepted);
        assertThat(downstreamHeader.get()).isEqualTo(accepted);
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER)).isEqualTo(accepted);
        assertThat(reactorRequestId.get()).isEqualTo(accepted);
        assertThat(body.get("requestId").asText()).isEqualTo(accepted);
        assertThat(existingTraceId.get()).isEqualTo("trace-existing");
    }

    @Test
    void replacesMissingAndInvalidRequestIdsWithTheGeneratedUuid() {
        assertGeneratedRequestId(null);
        assertGeneratedRequestId("");
        assertGeneratedRequestId("contains a space");
        assertGeneratedRequestId("x".repeat(65));
        assertGeneratedRequestId("unsafe/header\r\nvalue");
    }

    @Test
    void acceptsTheFullDocumentedCharacterSetAtTheMaximumLength() {
        String requestId = "Aa09._-" + "x".repeat(57);
        MockServerWebExchange exchange = exchangeWithRequestId(requestId);
        AtomicReference<String> downstream = new AtomicReference<>();

        filter().filter(exchange, filtered -> {
            downstream.set(filtered.getRequest().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER));
            return Mono.empty();
        }).block();

        assertThat(requestId).hasSize(64);
        assertThat(downstream.get()).isEqualTo(requestId);
    }

    @Test
    void runsAtTheFirstGatewayFilterOrder() {
        assertThat(filter().getOrder()).isEqualTo(GatewayFilterOrders.REQUEST_ID);
    }

    private static void assertGeneratedRequestId(String candidate) {
        MockServerWebExchange exchange = exchangeWithRequestId(candidate);
        AtomicReference<String> downstream = new AtomicReference<>();

        filter().filter(exchange, filtered -> {
            downstream.set(filtered.getRequest().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER));
            return Mono.empty();
        }).block();

        assertThat((String) exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID))
                .isEqualTo(GENERATED_UUID.toString());
        assertThat(downstream.get()).isEqualTo(GENERATED_UUID.toString());
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER))
                .isEqualTo(GENERATED_UUID.toString());
    }

    private static RequestIdWebFilter filter() {
        return new RequestIdWebFilter(new RequestIdPolicy(() -> GENERATED_UUID));
    }

    private static GatewayErrorWriter writer() {
        return new GatewayErrorWriter(OBJECT_MAPPER, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MockServerWebExchange exchangeWithRequestId(String requestId) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/v1/courses");
        if (requestId != null) {
            request.header(RequestContext.REQUEST_ID_HEADER, requestId);
        }
        return MockServerWebExchange.from(request.build());
    }
}
