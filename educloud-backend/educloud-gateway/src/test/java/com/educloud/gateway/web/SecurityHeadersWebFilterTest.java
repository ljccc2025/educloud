package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersWebFilterTest {

    private static final String HSTS = "Strict-Transport-Security";

    @Test
    void addsTheBaselineToGatewayAndProxiedResponsesAndRemovesVersionHeaders() {
        MockServerWebExchange exchange = exchange("http://gateway.local/api/v1/courses");

        filter("local").filter(exchange, filtered -> {
            filtered.getResponse().getHeaders().set(HttpHeaders.SERVER, "ReactorNetty/1.1");
            filtered.getResponse().getHeaders().set("X-Powered-By", "Spring");
            filtered.getResponse().getHeaders().set("X-Application-Context", "gateway:local:8080");
            return filtered.getResponse().setComplete();
        }).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getFirst("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(headers).doesNotContainKeys(HttpHeaders.SERVER, "X-Powered-By", "X-Application-Context");
        assertThat(headers).doesNotContainKey(HSTS);
    }

    @Test
    void preservesExistingPoliciesInsteadOfWeakeningThem() {
        MockServerWebExchange exchange = exchange("https://gateway.example/api/v1/courses");
        String stricter = "default-src 'none'; frame-ancestors 'none'; sandbox";

        filter("prod").filter(exchange, filtered -> {
            filtered.getResponse().getHeaders().set("Content-Security-Policy", stricter);
            return filtered.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
                .isEqualTo(stricter);
        assertThat(exchange.getResponse().getHeaders().getFirst(HSTS))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void emitsHstsOnlyForHttpsOutsideTheLocalEnvironment() {
        MockServerWebExchange localHttps = exchange("https://gateway.local/api/v1/courses");
        MockServerWebExchange prodHttp = exchange("http://gateway.example/api/v1/courses");

        filter("local").filter(localHttps, e -> e.getResponse().setComplete()).block();
        filter("prod").filter(prodHttp, e -> e.getResponse().setComplete()).block();

        assertThat(localHttps.getResponse().getHeaders())
                .doesNotContainKey(HSTS);
        assertThat(prodHttp.getResponse().getHeaders())
                .doesNotContainKey(HSTS);
    }

    private static SecurityHeadersWebFilter filter(String environment) {
        return new SecurityHeadersWebFilter(new GatewayRuntimeProperties(environment));
    }

    private static MockServerWebExchange exchange(String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uri));
    }
}
