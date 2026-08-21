package com.educloud.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.HttpClientProperties;

import java.time.Duration;
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

    @Test
    void rejectsNonInternalManagementAddress() {
        GatewayRuntimeProperties runtime = new GatewayRuntimeProperties("local");
        GatewayWebProperties web = webProperties("http://localhost:5173");

        assertThatThrownBy(() -> GatewayConfigurationValidator.validate(
                runtime, web, "0.0.0.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("management.server.address must be an internal management address");
    }

    @Test
    void rejectsMismatchedHttpClientConnectTimeout() {
        GatewayWebProperties web = new GatewayWebProperties();
        HttpClientProperties httpClient = httpClient(1000, web.getResponseTimeout());

        assertThatThrownBy(() -> GatewayConfigurationValidator.validateHttpClient(web, httpClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "validated Gateway connect timeout must match the actual HTTP client connect timeout");
    }

    @Test
    void rejectsMismatchedHttpClientResponseTimeout() {
        GatewayWebProperties web = new GatewayWebProperties();
        HttpClientProperties httpClient = httpClient(
                Math.toIntExact(web.getConnectTimeout().toMillis()),
                Duration.ofSeconds(10));

        assertThatThrownBy(() -> GatewayConfigurationValidator.validateHttpClient(web, httpClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "validated Gateway response timeout must match the actual HTTP client response timeout");
    }

    @Test
    void acceptsMatchingHttpClientTimeouts() {
        GatewayWebProperties web = new GatewayWebProperties();
        HttpClientProperties httpClient = httpClient(
                Math.toIntExact(web.getConnectTimeout().toMillis()),
                web.getResponseTimeout());

        assertThatCode(() -> GatewayConfigurationValidator.validateHttpClient(web, httpClient))
                .doesNotThrowAnyException();
    }

    private static HttpClientProperties httpClient(Integer connectTimeout, Duration responseTimeout) {
        HttpClientProperties properties = new HttpClientProperties();
        properties.setConnectTimeout(connectTimeout);
        properties.setResponseTimeout(responseTimeout);
        return properties;
    }

    private static GatewayWebProperties webProperties(String origin) {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setAllowedOrigins(List.of(origin));
        return properties;
    }
}
