package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.route.AccessPolicy;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestBodyCachingWebFilterTest {

    @Test
    void cachesChunkedAuthBodyAndReplaysIdenticalBytesAndHeaders() {
        byte[] body = "{\"loginName\":\"Alice\",\"password\":\"secret\"}"
                .getBytes(StandardCharsets.UTF_8);
        MockServerWebExchange exchange = exchange(
                "/api/v1/auth/login", body, new int[]{5, 7, body.length - 12}, true);
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        AtomicReference<byte[]> downstreamBody = new AtomicReference<>();
        AtomicReference<MediaType> contentType = new AtomicReference<>();
        AtomicReference<Long> contentLength = new AtomicReference<>();
        WebFilterChain chain = filtered -> DataBufferUtils.join(filtered.getRequest().getBody())
                .doOnNext(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    downstreamBody.set(bytes);
                    contentType.set(filtered.getRequest().getHeaders().getContentType());
                    contentLength.set(filtered.getRequest().getHeaders().getContentLength());
                })
                .then();

        filter(writer).filter(exchange, chain).block();

        assertThat(GatewayExchangeAttributes.cachedRequestBody(exchange)).isPresent();
        assertThat(GatewayExchangeAttributes.cachedRequestBody(exchange).orElseThrow()).containsExactly(body);
        assertThat(downstreamBody.get()).containsExactly(body);
        assertThat(contentType.get()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(contentLength.get()).isEqualTo((long) body.length);
        verify(writer, never()).write(any(), any());
    }

    @Test
    void enforcesEveryStreamingBodyLimitWithoutTrustingContentLength() {
        assertLimit("/api/v1/auth/register", 16 * 1024);
        assertLimit("/api/v1/auth/login", 16 * 1024);
        assertLimit("/api/v1/auth/refresh", 16 * 1024);
        assertLimit("/api/v1/payment-callbacks/alipay/notify", 256 * 1024);
        assertLimit("/api/v1/users/me", 1024 * 1024);
    }

    @Test
    void doesNotAllocateOrCacheAnAbsentBody() {
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/courses"));

        filter(writer).filter(exchange, chain).block();

        assertThat(GatewayExchangeAttributes.cachedRequestBody(exchange)).isEmpty();
        verify(chain).filter(any());
        verify(writer, never()).write(any(), any());
    }

    @Test
    void releasesPooledBuffersWhenTheBodyExceedsTheLimit() {
        NettyDataBufferFactory buffers = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        NettyDataBuffer first = (NettyDataBuffer) buffers.wrap(new byte[10 * 1024]);
        NettyDataBuffer second = (NettyDataBuffer) buffers.wrap(new byte[10 * 1024]);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Flux.just(first, second)));
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());

        filter(writer).filter(exchange, mock(WebFilterChain.class)).block();

        assertThat(first.getNativeBuffer().refCnt()).isZero();
        assertThat(second.getNativeBuffer().refCnt()).isZero();
        verify(writer).write(any(), org.mockito.ArgumentMatchers.argThat(
                (GatewayFailure failure) -> failure.code() == GatewayErrorCode.GATEWAY_REQUEST_TOO_LARGE));
    }

    @Test
    void releasesAccumulatedPooledBuffersWhenSubscriptionIsCancelled() {
        NettyDataBufferFactory buffers = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        NettyDataBuffer first = (NettyDataBuffer) buffers.wrap(new byte[1024]);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Flux.concat(Flux.just(first), Flux.never())));

        Disposable subscription = filter(mock(GatewayErrorWriter.class))
                .filter(exchange, mock(WebFilterChain.class))
                .subscribe();
        subscription.dispose();

        assertThat(first.getNativeBuffer().refCnt()).isZero();
    }

    @Test
    void runsBeforeIdentityAndRateLimitFilters() {
        assertThat(filter(mock(GatewayErrorWriter.class)).getOrder())
                .isEqualTo(GatewayFilterOrders.BODY_CACHE)
                .isLessThan(GatewayFilterOrders.CLIENT_IDENTITY)
                .isLessThan(GatewayFilterOrders.RATE_LIMIT);
    }

    private static void assertLimit(String path, int limit) {
        GatewayErrorWriter writer = mock(GatewayErrorWriter.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter(writer).filter(exchange(path, new byte[limit], new int[]{limit / 2, limit - limit / 2}, false), chain)
                .block();
        verify(chain).filter(any());

        GatewayErrorWriter oversizedWriter = mock(GatewayErrorWriter.class);
        when(oversizedWriter.write(any(), any())).thenReturn(Mono.empty());
        WebFilterChain blocked = mock(WebFilterChain.class);
        filter(oversizedWriter).filter(
                exchange(path, new byte[limit + 1], new int[]{limit / 2, limit + 1 - limit / 2}, false),
                blocked).block();
        verify(oversizedWriter).write(any(), org.mockito.ArgumentMatchers.argThat(
                (GatewayFailure failure) -> failure.code() == GatewayErrorCode.GATEWAY_REQUEST_TOO_LARGE));
        verify(blocked, never()).filter(any());
    }

    private static RequestBodyCachingWebFilter filter(GatewayErrorWriter writer) {
        return new RequestBodyCachingWebFilter(AccessPolicy.standard(), new GatewayWebProperties(), writer);
    }

    private static MockServerWebExchange exchange(
            String path, byte[] body, int[] chunkSizes, boolean contentLength) {
        DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
        AtomicInteger offset = new AtomicInteger();
        Flux<DataBuffer> chunks = Flux.range(0, chunkSizes.length)
                .map(chunkIndex -> {
                    int start = offset.getAndAdd(chunkSizes[chunkIndex]);
                    int end = start + chunkSizes[chunkIndex];
                    return buffers.wrap(Arrays.copyOfRange(body, start, end));
                });
        MockServerHttpRequest.BodyBuilder request = MockServerHttpRequest.post(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (contentLength) {
            request.contentLength(body.length);
        }
        return MockServerWebExchange.from(request.body(chunks));
    }
}
