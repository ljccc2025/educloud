package com.educloud.gateway.route;

import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.web.GatewayFilterOrders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalPathWebFilterTest {

    @Test
    void blocksInternalPathsBeforeSecurityRedisAndRouting() {
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);
        ReactiveJwtDecoder jwtDecoder = mock(ReactiveJwtDecoder.class);
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        RouteLocator routeLocator = mock(RouteLocator.class);
        InternalPathWebFilter filter = new InternalPathWebFilter(AccessPolicy.standard(), writer);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/internal/v1/jobs/123"));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<GatewayFailure> failure = ArgumentCaptor.forClass(GatewayFailure.class);
        verify(writer).write(any(), failure.capture());
        assertThat(failure.getValue().code()).isEqualTo(GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND);
        verify(chain, never()).filter(any());
        verifyNoInteractions(jwtDecoder, redis, routeLocator);
    }

    @Test
    void allowsExternalPathsToContinue() {
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        InternalPathWebFilter filter = new InternalPathWebFilter(AccessPolicy.standard(), writer);

        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")), chain).block();

        verify(chain).filter(any());
        verifyNoInteractions(writer);
    }

    @Test
    void runsBeforeBodySecurityAndRateLimitFilters() {
        InternalPathWebFilter filter = new InternalPathWebFilter(
                AccessPolicy.standard(), mock(GatewayErrorWriter.class));

        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.INTERNAL_PATH);
        assertThat(filter.getOrder()).isLessThan(GatewayFilterOrders.BODY_CACHE);
        assertThat(filter.getOrder()).isLessThan(GatewayFilterOrders.CLIENT_IDENTITY);
        assertThat(filter.getOrder()).isLessThan(GatewayFilterOrders.RATE_LIMIT);
    }
}
