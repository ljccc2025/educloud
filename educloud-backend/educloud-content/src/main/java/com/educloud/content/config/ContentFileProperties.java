package com.educloud.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "educloud.content.file")
public record ContentFileProperties(
        String endpoint,
        String clientId,
        String clientSecret,
        boolean enabled,
        Duration timeout,
        String tokenEndpoint) {
}
