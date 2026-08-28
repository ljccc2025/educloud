package com.educloud.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "educloud.ai")
public record AiProperties(
        ProviderProperties provider,
        TimeoutProperties timeout,
        QuotaProperties quota,
        ContextProperties context,
        JwtProperties jwt) {

    public record ProviderProperties(
            String name,
            String baseUrl,
            String model,
            String apiKey,
            boolean thinkingEnabled,
            int maxTokens) {
    }

    public record TimeoutProperties(long connectMs, long readMs) {
    }

    public record QuotaProperties(int dailyRequests, long dailyTokens) {
    }

    public record ContextProperties(int maxHistoryMessages, int maxPromptTokens) {
    }

    public record JwtProperties(String jwksLocation, String issuer, String audience) {
    }
}
