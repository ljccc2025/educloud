package com.educloud.gateway;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.educloud.gateway.security.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GatewayApplicationContextTest {

    @Test
    void startsTheCompleteGatewayContextWithoutExternalConnections() {
        String jwks = new TestJwtKeys().publicJwksJson();
        String hmacSecret = Base64.getEncoder().encodeToString(new byte[32]);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                GatewayApplication.class, ExternalDependencyConfiguration.class)
                .web(WebApplicationType.REACTIVE)
                .run(
                        "--server.port=0",
                        "--management.server.address=127.0.0.1",
                        "--management.server.port=0",
                        "--management.tracing.sampling.probability=0.0",
                        "--spring.main.banner-mode=off",
                        "--spring.config.import=",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.discovery.register-enabled=false",
                        "--educloud.gateway.environment=local",
                        "--educloud.gateway.security.jwks-json=" + jwks,
                        "--educloud.gateway.security.issuer=https://issuer.educloud.local",
                        "--educloud.gateway.security.audience=educloud-api",
                        "--educloud.gateway.ratelimit.hmac-secret-base64=" + hmacSecret,
                        "--educloud.gateway.nacos.server-addr=127.0.0.1:8848",
                        "--educloud.gateway.nacos.namespace=context-test",
                        "--educloud.gateway.nacos.config-group=EDUCLOUD_GATEWAY",
                        "--educloud.gateway.nacos.discovery-group=EDUCLOUD_SERVICES",
                        "--educloud.gateway.nacos.username=context-test",
                        "--educloud.gateway.nacos.password=context-test-password")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalDependencyConfiguration {

        @Bean
        NacosServiceManager nacosServiceManager() {
            return mock(NacosServiceManager.class);
        }
    }
}
