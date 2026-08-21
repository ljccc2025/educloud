package com.educloud.gateway.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public final class GatewayConfigurationValidator implements SmartInitializingSingleton {

    private final GatewayRuntimeProperties runtimeProperties;
    private final GatewayWebProperties webProperties;
    private final HttpClientProperties httpClientProperties;
    private final String managementAddress;

    public GatewayConfigurationValidator(
            GatewayRuntimeProperties runtimeProperties,
            GatewayWebProperties webProperties,
            HttpClientProperties httpClientProperties,
            @Value("${management.server.address:}") String managementAddress) {
        this.runtimeProperties = runtimeProperties;
        this.webProperties = webProperties;
        this.httpClientProperties = httpClientProperties;
        this.managementAddress = managementAddress;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate(runtimeProperties, webProperties, managementAddress);
        validateHttpClient(webProperties, httpClientProperties);
    }

    public static void validate(
            GatewayRuntimeProperties runtimeProperties,
            GatewayWebProperties webProperties,
            String managementAddress) {
        if (!"local".equals(runtimeProperties.environment())) {
            boolean hasNonHttpsOrigin = webProperties.getAllowedOrigins().stream()
                    .map(URI::create)
                    .anyMatch(origin -> !"https".equalsIgnoreCase(origin.getScheme()));
            if (hasNonHttpsOrigin) {
                throw new IllegalStateException("non-local environments require exact HTTPS origins");
            }
        }
        if (!isInternalManagementAddress(managementAddress)) {
            throw new IllegalStateException(
                    "management.server.address must be an internal management address");
        }
    }

    public static void validateHttpClient(
            GatewayWebProperties webProperties,
            HttpClientProperties httpClientProperties) {
        if (webProperties == null || httpClientProperties == null) {
            throw new IllegalStateException("gateway timeout configuration must be available");
        }
        Integer connectTimeout = httpClientProperties.getConnectTimeout();
        if (connectTimeout == null
                || webProperties.getConnectTimeout() == null
                || webProperties.getConnectTimeout().toMillis() != connectTimeout.longValue()) {
            throw new IllegalStateException(
                    "validated Gateway connect timeout must match the actual HTTP client connect timeout");
        }
        if (webProperties.getResponseTimeout() == null
                || !webProperties.getResponseTimeout().equals(httpClientProperties.getResponseTimeout())) {
            throw new IllegalStateException(
                    "validated Gateway response timeout must match the actual HTTP client response timeout");
        }
    }

    private static boolean isInternalManagementAddress(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String addressText = stripIpv6Brackets(value.trim());
        if ("localhost".equalsIgnoreCase(addressText)) {
            return true;
        }
        if (!addressText.matches("[0-9.]+") && !addressText.matches("[0-9A-Fa-f:]+")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(addressText);
            if (address.isAnyLocalAddress()) {
                return false;
            }
            if (address.isLoopbackAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            if (address instanceof Inet6Address) {
                return (bytes[0] & 0xfe) == 0xfc;
            }
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 10
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static String stripIpv6Brackets(String value) {
        if (value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
