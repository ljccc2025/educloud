package com.educloud.gateway.error;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayWebExceptionHandlerTest {

    @Test
    void mapsKnownReactiveEdgeFailuresToTheStableJsonContract() {
        for (FailureCase failure : List.of(
                new FailureCase(new ResponseStatusException(HttpStatus.NOT_FOUND, "dynamic path"),
                        HttpStatus.NOT_FOUND, GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND),
                new FailureCase(NotFoundException.create(false, "service name"),
                        HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCode.DEPENDENCY_UNAVAILABLE),
                new FailureCase(new ConnectException("private host"),
                        HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCode.DEPENDENCY_UNAVAILABLE),
                new FailureCase(ReadTimeoutException.INSTANCE,
                        HttpStatus.GATEWAY_TIMEOUT, GatewayErrorCode.GATEWAY_TIMEOUT),
                new FailureCase(new TimeoutException("private timeout detail"),
                        HttpStatus.GATEWAY_TIMEOUT, GatewayErrorCode.GATEWAY_TIMEOUT),
                new FailureCase(new DataBufferLimitException("body bytes"),
                        HttpStatus.PAYLOAD_TOO_LARGE, GatewayErrorCode.GATEWAY_REQUEST_TOO_LARGE),
                new FailureCase(new ServerWebInputException("unsafe input"),
                        HttpStatus.BAD_REQUEST, GatewayErrorCode.GATEWAY_BAD_REQUEST),
                new FailureCase(new DecodingException("unsafe parser detail"),
                        HttpStatus.BAD_REQUEST, GatewayErrorCode.GATEWAY_BAD_REQUEST),
                new FailureCase(new IllegalStateException("internal secret"),
                        HttpStatus.INTERNAL_SERVER_ERROR, GatewayErrorCode.INTERNAL_ERROR))) {
            MockServerWebExchange exchange = exchange();

            handler().handle(exchange, failure.throwable()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(failure.status());
            String body = exchange.getResponse().getBodyAsString().block();
            assertThat(body)
                    .contains("\"code\":\"" + failure.code().name() + "\"")
                    .contains("\"requestId\":\"request-123\"")
                    .doesNotContain("dynamic path", "service name", "private host",
                            "private timeout detail", "body bytes", "unsafe input",
                            "unsafe parser detail", "internal secret");
        }
    }

    @Test
    void unwrapsKnownNetworkCausesButDoesNotRewriteCommittedResponses() {
        MockServerWebExchange wrapped = exchange();
        handler().handle(wrapped,
                new RuntimeException("wrapper", new ConnectException("private host"))).block();
        assertThat(wrapped.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        MockServerWebExchange committed = exchange();
        committed.getResponse().setStatusCode(HttpStatus.ACCEPTED);
        committed.getResponse().setComplete().block();
        RuntimeException failure = new RuntimeException("late failure");
        assertThatThrownBy(() -> handler().handle(committed, failure).block())
                .isSameAs(failure);
        assertThat(committed.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void unknownFailuresDoNotCopyThrowableDetailsIntoLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(GatewayWebExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler().handle(exchange(), new IllegalStateException("Bearer sensitive-token")).block();

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("sensitive-token");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static GatewayWebExceptionHandler handler() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new GatewayWebExceptionHandler(new GatewayErrorWriter(
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC)));
    }

    private static MockServerWebExchange exchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses"));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "request-123");
        return exchange;
    }

    private record FailureCase(
            Throwable throwable, HttpStatus status, GatewayErrorCode code) {
    }
}
