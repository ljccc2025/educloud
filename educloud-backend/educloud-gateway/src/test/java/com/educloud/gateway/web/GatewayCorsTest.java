package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayCorsTest {

    private static final String ALLOWED = "http://localhost:5173";

    @Test
    void acceptsOnlyExactConfiguredOriginsAndLeavesServerRequestsAlone() {
        WebTestClient client = client();

        client.get().uri("/api/v1/courses").header(HttpHeaders.ORIGIN, ALLOWED)
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        client.get().uri("/api/v1/courses")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);

        for (String rejected : List.of(
                "http://localhost:5173.evil.example",
                "null",
                "*",
                "https://localhost:5173")) {
            client.get().uri("/api/v1/courses").header(HttpHeaders.ORIGIN, rejected)
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().contentTypeCompatibleWith("application/json")
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
        }
    }

    @Test
    void terminatesValidPreflightWithTheExplicitCorsContract() {
        client().options().uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, ALLOWED)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Authorization, Content-Type, If-Match, Accept-Language")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> assertThat(value).contains("PATCH"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> assertThat(value)
                                .containsIgnoringCase("Authorization")
                                .containsIgnoringCase("Content-Type")
                                .containsIgnoringCase("If-Match")
                                .containsIgnoringCase("Accept-Language"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        value -> assertThat(value)
                                .containsIgnoringCase("X-Request-Id")
                                .containsIgnoringCase("Retry-After"));
    }

    @Test
    void mapsDisallowedPreflightMethodsAndHeadersToTheGatewayJsonContract() {
        WebTestClient client = client();

        assertRejectedPreflight(client, "CONNECT", "Authorization");
        assertRejectedPreflight(client, "POST", "X-Evil-Header");
    }

    private static void assertRejectedPreflight(
            WebTestClient client, String requestedMethod, String requestedHeaders) {
        client.options().uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, ALLOWED)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestedMethod)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestedHeaders)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    private static WebTestClient client() {
        GatewayWebProperties properties = properties();
        GatewayErrorWriter writer = new GatewayErrorWriter(
                objectMapper(), Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));
        CorsWebFilter cors = new CorsConfiguration().gatewayCorsWebFilter(properties);
        return WebTestClient.bindToWebHandler(exchange -> {
                    exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                    return exchange.getResponse().setComplete();
                })
                .webFilter(
                        new RequestIdWebFilter(),
                        new OriginPolicyWebFilter(properties, writer),
                        cors)
                .configureClient()
                .baseUrl("http://gateway.local")
                .build();
    }

    static GatewayWebProperties properties() {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:5175"));
        return properties;
    }

    static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
