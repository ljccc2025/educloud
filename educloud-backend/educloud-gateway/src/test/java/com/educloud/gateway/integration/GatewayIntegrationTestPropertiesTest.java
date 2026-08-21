package com.educloud.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayIntegrationTestPropertiesTest {

    @Test
    void overridesBlankValuesLoadedFromApplicationConfiguration() {
        String propertyName = "educloud.gateway.ratelimit.hmac-secret-base64";
        String testSecret = "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==";
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "applicationConfig",
                Map.of(propertyName, "")));

        String[] arguments = GatewayIntegrationTestProperties.asArguments(Map.of(propertyName, testSecret));
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(arguments));

        assertThat(environment.getProperty(propertyName)).isEqualTo(testSecret);
    }
}
