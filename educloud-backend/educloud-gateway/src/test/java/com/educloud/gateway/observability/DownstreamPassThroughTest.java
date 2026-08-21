package com.educloud.gateway.observability;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import com.educloud.gateway.web.RequestIdWebFilter;
import com.educloud.gateway.web.SecurityHeadersWebFilter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamPassThroughTest {

    @Test
    void preservesDownstreamStatusBodyAndContentTypeWithoutWrapping() {
        AtomicReference<String> downstreamTraceparent = new AtomicReference<>();
        AtomicReference<String> downstreamTracestate = new AtomicReference<>();
        DisposableServer downstream = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    downstreamTraceparent.set(request.requestHeaders().get("traceparent"));
                    downstreamTracestate.set(request.requestHeaders().get("tracestate"));
                    Response reply = switch (request.uri()) {
                        case "/ok" -> new Response(200, "{\"data\":\"ok\"}");
                        case "/bad" -> new Response(400, "{\"code\":\"BUSINESS_RULE\"}");
                        default -> new Response(404, "{\"code\":\"COURSE_NOT_FOUND\"}");
                    };
                    response.status(HttpResponseStatus.valueOf(reply.status()));
                    response.header(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    return response.sendString(Mono.just(reply.body()));
                })
                .bindNow();
        try {
            WebClient proxyClient = WebClient.create("http://127.0.0.1:" + downstream.port());
            WebHandler proxy = exchange -> proxyClient.get()
                    .uri(exchange.getRequest().getPath().value())
                    .headers(headers -> copyTraceHeaders(exchange.getRequest().getHeaders(), headers))
                    .exchangeToMono(response -> response.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .flatMap(body -> {
                                exchange.getResponse().setStatusCode(
                                        HttpStatusCode.valueOf(response.statusCode().value()));
                                response.headers().contentType().ifPresent(
                                        exchange.getResponse().getHeaders()::setContentType);
                                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
                                return exchange.getResponse().writeWith(Mono.just(buffer));
                            }));
            WebTestClient client = WebTestClient.bindToWebHandler(proxy)
                    .webFilter(
                            new RequestIdWebFilter(),
                            new SecurityHeadersWebFilter(new GatewayRuntimeProperties("local")))
                    .build();

            for (Response expected : List.of(
                    new Response(200, "{\"data\":\"ok\"}"),
                    new Response(400, "{\"code\":\"BUSINESS_RULE\"}"),
                    new Response(404, "{\"code\":\"COURSE_NOT_FOUND\"}"))) {
                String path = expected.status() == 200 ? "/ok" : expected.status() == 400 ? "/bad" : "/missing";
                client.get().uri(path)
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                        .header("tracestate", "vendor=value")
                        .exchange()
                        .expectStatus().isEqualTo(expected.status())
                        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                        .expectHeader().exists("X-Request-Id")
                        .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                        .expectBody().json(expected.body(), true);
            }
            assertThat(downstreamTraceparent).hasValue(
                    "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            assertThat(downstreamTracestate).hasValue("vendor=value");
        } finally {
            downstream.disposeNow();
        }
    }

    private static void copyTraceHeaders(HttpHeaders source, HttpHeaders target) {
        for (String name : List.of("traceparent", "tracestate")) {
            String value = source.getFirst(name);
            if (value != null) {
                target.set(name, value);
            }
        }
    }

    private record Response(int status, String body) {
    }
}
