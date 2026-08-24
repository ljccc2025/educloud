package com.educloud.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "educloud.order")
public record OrderProperties(
        String environment,
        JwtProperties jwt,
        InternalProperties internal) {

    public record JwtProperties(
            String jwksLocation,
            String issuer,
            String audience) {
    }

    public record InternalProperties(
            String allowedClientIds,
            String audience,
            String secretToken) {

        public List<String> effectiveAllowedClientIds() {
            if (allowedClientIds == null || allowedClientIds.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedClientIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        public String effectiveInternalAudience() {
            return (audience != null && !audience.isBlank()) ? audience : "educloud-order";
        }
    }
}
