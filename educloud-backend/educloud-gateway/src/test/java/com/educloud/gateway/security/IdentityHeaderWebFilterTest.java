package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.web.ClientIpResolver;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.educloud.gateway.web.GatewayFilterOrders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityHeaderWebFilterTest {

    @Test
    void storesClientIpPrivatelyAndRemovesForwardingAndIdentityHeaders() {
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        IdentityHeaderWebFilter filter = new IdentityHeaderWebFilter(resolver(), writer);
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        WebFilterChain chain = exchange -> {
            downstream.set(exchange);
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 12345))
                .header("Forwarded", "for=198.51.100.25")
                .header("X-Forwarded-For", "198.51.100.25")
                .header("X-Forwarded-Host", "spoofed.example")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Port", "443")
                .header("x-user-id", "spoofed-user")
                .header("X-USER-TYPE", "ADMIN")
                .header("X-Role", "admin")
                .header("X-Roles", "admin,user")
                .header("X-Permission", "*")
                .header("X-Permissions", "*")
                .header("X-Authenticated-User", "spoofed")
                .header("x-educloud-identity-extra", "spoofed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer original-token")
                .header("X-Request-Id", "request-123")
                .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                .header("tracestate", "vendor=value")
                .build());

        filter.filter(exchange, chain).block();

        assertThat((String) exchange.getAttribute(GatewayExchangeAttributes.CLIENT_IP))
                .isEqualTo("198.51.100.25");
        HttpHeaders headers = downstream.get().getRequest().getHeaders();
        assertThat(headers.keySet()).noneMatch(IdentityHeaderWebFilterTest::isForbiddenHeader);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer original-token");
        assertThat(headers.getFirst("X-Request-Id")).isEqualTo("request-123");
        assertThat(headers.getFirst("traceparent")).isNotBlank();
        assertThat(headers.getFirst("tracestate")).isEqualTo("vendor=value");
        assertThat(headers.values()).noneMatch(values -> values.contains("198.51.100.25"));
        verify(writer, never()).write(any(), any());
    }

    @Test
    void mapsMalformedTrustedProxyInputToSafe400WithoutCallingDownstream() {
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        IdentityHeaderWebFilter filter = new IdentityHeaderWebFilter(resolver(), writer);
        WebFilterChain chain = mock(WebFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 12345))
                .header("Forwarded", "for=unknown")
                .build());

        filter.filter(exchange, chain).block();

        verify(writer).write(any(), org.mockito.ArgumentMatchers.argThat(
                (GatewayFailure failure) -> failure.code() == GatewayErrorCode.GATEWAY_BAD_REQUEST));
        verify(chain, never()).filter(any());
    }

    @Test
    void runsAtTheFixedClientIdentityOrder() {
        IdentityHeaderWebFilter filter = new IdentityHeaderWebFilter(resolver(), mock(GatewayErrorWriter.class));

        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.CLIENT_IDENTITY);
    }

    private static ClientIpResolver resolver() {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        properties.setTrustedProxyHops(1);
        return new ClientIpResolver(properties);
    }

    private static boolean isForbiddenHeader(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("forwarded")
                || lower.startsWith("x-forwarded-")
                || lower.equals("x-user-id")
                || lower.equals("x-user-type")
                || lower.equals("x-role")
                || lower.equals("x-roles")
                || lower.equals("x-permission")
                || lower.equals("x-permissions")
                || lower.equals("x-authenticated-user")
                || lower.startsWith("x-educloud-identity-");
    }
}
