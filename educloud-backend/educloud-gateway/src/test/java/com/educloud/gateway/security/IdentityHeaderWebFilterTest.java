package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.web.ClientIpResolver;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.educloud.gateway.web.GatewayFilterOrders;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IdentityHeaderWebFilter 单元测试（MockServerWebExchange，无 Spring 上下文）：
 * 1. 注入解析出的可信真实客户端 IP 到 X-Real-IP，覆盖客户端伪造值（BUG-040/071/077）；
 * 2. 剥离客户端伪造的 x-real-ip / x-forwarded-* / forwarded / 身份头；
 * 3. 客户端 IP 解析失败时拒绝请求（写网关错误响应）。
 */
class IdentityHeaderWebFilterTest {

    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final GatewayErrorWriter errorWriter = mock(GatewayErrorWriter.class);
    private final IdentityHeaderWebFilter filter =
            new IdentityHeaderWebFilter(clientIpResolver, errorWriter);

    private ServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> requestBuilder) {
        MockServerWebExchange exchange = MockServerWebExchange.from(requestBuilder.build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        filter.filter(exchange, downstreamExchange -> {
            downstream.set(downstreamExchange);
            return Mono.empty();
        }).block();
        return downstream.get();
    }

    @Test
    @DisplayName("注入解析出的真实客户端 IP 到 X-Real-IP，并写入 exchange 属性")
    void injectsResolvedClientIpAndSetsExchangeAttribute() {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.7");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses").build());

        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        filter.filter(exchange, downstreamExchange -> {
            downstream.set(downstreamExchange);
            return Mono.empty();
        }).block();

        assertThat(downstream.get().getRequest().getHeaders().getFirst("X-Real-IP"))
                .isEqualTo("203.0.113.7");
        // getAttribute 为泛型方法 <T> T：先赋给 Object 变量避免与 assertThat 的 Predicate 重载产生推断歧义
        Object clientIp = exchange.getAttribute(GatewayExchangeAttributes.CLIENT_IP);
        assertThat(clientIp).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("客户端伪造 x-real-ip 被覆盖为解析出的真实 IP")
    void forgedRealIpReplacedWithResolvedClientIp() {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.7");
        ServerWebExchange downstream = run(MockServerHttpRequest.get("/api/v1/courses")
                .header("x-real-ip", "6.6.6.6"));

        HttpHeaders headers = downstream.getRequest().getHeaders();
        assertThat(headers.getFirst("X-Real-IP")).isEqualTo("203.0.113.7");
        assertThat(headers.get("x-real-ip")).containsExactly("203.0.113.7");
    }

    @Test
    @DisplayName("客户端伪造 x-forwarded-for / x-forwarded-host / forwarded 全部剥离")
    void forwardedHeadersStripped() {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.7");
        ServerWebExchange downstream = run(MockServerHttpRequest.get("/api/v1/courses")
                .header("x-forwarded-for", "6.6.6.6")
                .header("x-forwarded-host", "evil.example")
                .header("x-forwarded-proto", "http")
                .header("Forwarded", "for=6.6.6.6;proto=http"));

        HttpHeaders headers = downstream.getRequest().getHeaders();
        assertThat(headers.containsKey("x-forwarded-for")).isFalse();
        assertThat(headers.containsKey("x-forwarded-host")).isFalse();
        assertThat(headers.containsKey("x-forwarded-proto")).isFalse();
        assertThat(headers.containsKey("forwarded")).isFalse();
    }

    @Test
    @DisplayName("身份头与 x-educloud-identity-* 剥离，普通请求头保留")
    void identityHeadersStrippedButOrdinaryHeadersKept() {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.7");
        ServerWebExchange downstream = run(MockServerHttpRequest.get("/api/v1/courses")
                .header("x-user-id", "999")
                .header("x-user-type", "STUDENT")
                .header("x-authenticated-user", "alice")
                .header("x-educloud-identity-client", "c1")
                .header("x-request-id", "req-123"));

        HttpHeaders headers = downstream.getRequest().getHeaders();
        assertThat(headers.containsKey("x-user-id")).isFalse();
        assertThat(headers.containsKey("x-user-type")).isFalse();
        assertThat(headers.containsKey("x-authenticated-user")).isFalse();
        assertThat(headers.containsKey("x-educloud-identity-client")).isFalse();
        assertThat(headers.getFirst("x-request-id")).isEqualTo("req-123");
    }

    @Test
    @DisplayName("客户端 IP 无法安全解析时拒绝请求并写网关错误")
    void unresolvableClientIpWritesGatewayError() {
        when(clientIpResolver.resolve(any()))
                .thenThrow(new ClientIpResolver.ClientIpResolutionException());
        when(errorWriter.write(any(), any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/courses").build());

        filter.filter(exchange, downstreamExchange -> Mono.empty()).block();

        verify(errorWriter).write(any(), any());
    }

    @Test
    @DisplayName("可信代理输入畸形时映射为安全 400 且不调用下游")
    void mapsMalformedTrustedProxyInputToSafe400WithoutCallingDownstream() {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        properties.setTrustedProxyHops(1);
        IdentityHeaderWebFilter realFilter =
                new IdentityHeaderWebFilter(new ClientIpResolver(properties), errorWriter);
        when(errorWriter.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 12345))
                .header("Forwarded", "for=unknown")
                .build());

        realFilter.filter(exchange, chain).block();

        verify(errorWriter).write(any(), argThat((GatewayFailure failure) ->
                failure.code() == GatewayErrorCode.GATEWAY_BAD_REQUEST));
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("过滤器固定在客户端身份链路顺序上")
    void runsAtTheFixedClientIdentityOrder() {
        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.CLIENT_IDENTITY);
    }

    @Test
    @DisplayName("保留鉴权/链路头，且下游任何头值都不含伪造客户端 IP")
    void preservesAuthorizationAndTraceHeadersWithoutLeakingSpoofedIp() {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.7");
        ServerWebExchange downstream = run(MockServerHttpRequest.get("/api/v1/courses")
                .header("x-forwarded-for", "198.51.100.25")
                .header(HttpHeaders.AUTHORIZATION, "Bearer original-token")
                .header("X-Request-Id", "request-123")
                .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                .header("tracestate", "vendor=value"));

        HttpHeaders headers = downstream.getRequest().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer original-token");
        assertThat(headers.getFirst("X-Request-Id")).isEqualTo("request-123");
        assertThat(headers.getFirst("traceparent")).isNotBlank();
        assertThat(headers.getFirst("tracestate")).isEqualTo("vendor=value");
        assertThat(headers.values()).noneMatch(values -> values.contains("198.51.100.25"));
    }
}
