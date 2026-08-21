package com.educloud.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayConfigurationValidatorTest {

    private static final String INTERNAL_MANAGEMENT_ADDRESS = "127.0.0.1";

    @Test
    void rejectsNonHttpsOriginsForNonLocalEnvironments() {
        GatewayRuntimeProperties runtime = new GatewayRuntimeProperties("staging");
        GatewayWebProperties web = webProperties("http://localhost:5173");

        assertThatThrownBy(() -> GatewayConfigurationValidator.validate(
                runtime, web, INTERNAL_MANAGEMENT_ADDRESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("non-local environments require exact HTTPS origins");
    }

    @Test
    void acceptsExactHttpsOriginsForNonLocalEnvironments() {
        GatewayRuntimeProperties runtime = new GatewayRuntimeProperties("production");
        GatewayWebProperties web = webProperties("https://app.educloud.local");

        assertThatCode(() -> GatewayConfigurationValidator.validate(
                runtime, web, INTERNAL_MANAGEMENT_ADDRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsHttpOriginsForLocalEnvironment() {
        GatewayRuntimeProperties runtime = new GatewayRuntimeProperties("local");
        GatewayWebProperties web = webProperties("http://localhost:5173");

        assertThatCode(() -> GatewayConfigurationValidator.validate(
                runtime, web, INTERNAL_MANAGEMENT_ADDRESS))
                .doesNotThrowAnyException();
    }

    private static GatewayWebProperties webProperties(String origin) {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setAllowedOrigins(List.of(origin));
        return properties;
    }
}
