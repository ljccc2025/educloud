package com.educloud.gateway.config;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NettyRequestBoundaryConfiguration {

    private static final String INVALID_LIMIT_MESSAGE =
            "Netty request decoder limits must be positive 32-bit byte counts";

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyRequestBoundaryCustomizer(
            GatewayWebProperties properties) {
        return factory -> {
            int headerLimit = positiveInt(properties.getHeaderLimit().toBytes());
            int initialLineLimit = positiveInt(properties.getInitialLineLimit().toBytes());
            factory.addServerCustomizers(server -> server.httpRequestDecoder(spec -> spec
                    .maxHeaderSize(headerLimit)
                    .maxInitialLineLength(initialLineLimit)
                    .validateHeaders(true)));
        };
    }

    private static int positiveInt(long bytes) {
        try {
            int value = Math.toIntExact(bytes);
            if (value <= 0) {
                throw new IllegalStateException(INVALID_LIMIT_MESSAGE);
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(INVALID_LIMIT_MESSAGE);
        }
    }
}
