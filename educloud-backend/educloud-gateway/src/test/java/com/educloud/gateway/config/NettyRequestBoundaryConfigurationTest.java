package com.educloud.gateway.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.util.unit.DataSize;
import reactor.netty.http.server.HttpRequestDecoderSpec;
import reactor.netty.http.server.HttpServer;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NettyRequestBoundaryConfigurationTest {

    @Test
    void appliesTheConfiguredHeaderAndInitialLineLimitsAndEnablesValidation() {
        GatewayWebProperties properties = new GatewayWebProperties();
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer =
                new NettyRequestBoundaryConfiguration().nettyRequestBoundaryCustomizer(properties);

        customizer.customize(factory);

        assertThat(factory.getServerCustomizers()).hasSize(1);
        HttpServer server = mock(HttpServer.class);
        when(server.httpRequestDecoder(any())).thenReturn(server);
        NettyServerCustomizer serverCustomizer = factory.getServerCustomizers().iterator().next();
        serverCustomizer.apply(server);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<HttpRequestDecoderSpec, HttpRequestDecoderSpec>> decoder =
                ArgumentCaptor.forClass(Function.class);
        verify(server).httpRequestDecoder(decoder.capture());
        HttpRequestDecoderSpec spec = mock(HttpRequestDecoderSpec.class);
        when(spec.maxHeaderSize(any(Integer.class))).thenReturn(spec);
        when(spec.maxInitialLineLength(any(Integer.class))).thenReturn(spec);
        when(spec.validateHeaders(any(Boolean.class))).thenReturn(spec);

        assertThat(decoder.getValue().apply(spec)).isSameAs(spec);
        verify(spec).maxHeaderSize(16 * 1024);
        verify(spec).maxInitialLineLength(8 * 1024);
        verify(spec).validateHeaders(true);
    }

    @Test
    void rejectsNonPositiveAndOverflowingDecoderLimitsBeforeStartup() {
        for (DataSize invalid : new DataSize[]{
                DataSize.ofBytes(0),
                DataSize.ofBytes((long) Integer.MAX_VALUE + 1)}) {
            GatewayWebProperties properties = new GatewayWebProperties();
            properties.setHeaderLimit(invalid);
            WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer =
                    new NettyRequestBoundaryConfiguration().nettyRequestBoundaryCustomizer(properties);

            assertThatThrownBy(() -> customizer.customize(new NettyReactiveWebServerFactory()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Netty request decoder limits must be positive 32-bit byte counts");
        }
    }
}
