package com.educloud.gateway.integration;

import java.util.Map;

final class GatewayIntegrationTestProperties {

    private GatewayIntegrationTestProperties() {
    }

    static String[] asArguments(Map<String, String> properties) {
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }
}
