package com.educloud.gateway.error;

import com.educloud.gateway.web.GatewayExchangeAttributes;
import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@Component
public final class GatewayWebExceptionHandler implements WebExceptionHandler, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayWebExceptionHandler.class);
    private static final int ORDER_BEFORE_BOOT_ERROR_HANDLER = -2;

    private final GatewayErrorWriter errorWriter;

    public GatewayWebExceptionHandler(GatewayErrorWriter errorWriter) {
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(failure);
        }
        GatewayErrorCode code = map(failure);
        if (code == GatewayErrorCode.INTERNAL_ERROR) {
            LOGGER.error(
                    "Unhandled gateway exception requestId={} category=internal exceptionType={}",
                    requestId(exchange), failure.getClass().getName());
        }
        return errorWriter.write(exchange, GatewayFailure.of(code));
    }

    @Override
    public int getOrder() {
        return ORDER_BEFORE_BOOT_ERROR_HANDLER;
    }

    private static GatewayErrorCode map(Throwable failure) {
        for (Throwable current : causes(failure)) {
            if (current instanceof DataBufferLimitException) {
                return GatewayErrorCode.GATEWAY_REQUEST_TOO_LARGE;
            }
            if (current instanceof ReadTimeoutException || current instanceof TimeoutException) {
                return GatewayErrorCode.GATEWAY_TIMEOUT;
            }
            if (current instanceof ConnectException) {
                return GatewayErrorCode.DEPENDENCY_UNAVAILABLE;
            }
            if (current instanceof NotFoundException notFound) {
                return HttpStatus.NOT_FOUND.equals(notFound.getStatusCode())
                        ? GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND
                        : GatewayErrorCode.DEPENDENCY_UNAVAILABLE;
            }
            if (current instanceof ResponseStatusException status
                    && HttpStatus.NOT_FOUND.equals(status.getStatusCode())) {
                return GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND;
            }
            if (current instanceof ServerWebInputException || current instanceof DecodingException) {
                return GatewayErrorCode.GATEWAY_BAD_REQUEST;
            }
        }
        return GatewayErrorCode.INTERNAL_ERROR;
    }

    private static Iterable<Throwable> causes(Throwable failure) {
        java.util.List<Throwable> causes = new java.util.ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && seen.add(current)) {
            causes.add(current);
            current = current.getCause();
        }
        return causes;
    }

    private static String requestId(ServerWebExchange exchange) {
        Object requestId = exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID);
        return requestId instanceof String value && !value.isBlank() ? value : "unavailable";
    }
}
