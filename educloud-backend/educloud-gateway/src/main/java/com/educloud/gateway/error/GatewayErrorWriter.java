package com.educloud.gateway.error;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.web.RequestContext;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

@Component
public final class GatewayErrorWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayErrorWriter.class);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GatewayErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Mono<Void> write(ServerWebExchange exchange, GatewayFailure failure) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(failure, "failure");
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        String requestId = GatewayExchangeAttributes.requireRequestId(exchange);
        ApiResponse<Void> body = new ApiResponse<>(
                failure.code().code(),
                failure.publicMessage(),
                null,
                requestId,
                clock.instant());

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException | RuntimeException exception) {
            LOGGER.error("Failed to serialize gateway error response requestId={} category=serialization", requestId);
            exchange.getResponse().getHeaders().set(HttpHeaders.CONNECTION, "close");
            return exchange.getResponse().setComplete();
        }

        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(failure.code().httpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(RequestContext.REQUEST_ID_HEADER, requestId);
        failure.retryAfter().ifPresent(retryAfter -> exchange.getResponse().getHeaders()
                .set(HttpHeaders.RETRY_AFTER, Long.toString(ceilSeconds(retryAfter))));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static long ceilSeconds(Duration duration) {
        long seconds = duration.getSeconds();
        if (duration.getNano() > 0) {
            if (seconds == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            seconds++;
        }
        return Math.max(1, seconds);
    }
}
