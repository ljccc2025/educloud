package com.educloud.ai.dto.response;

public record AiUsageResponse(int promptTokens, int completionTokens, int totalTokens) {
}
