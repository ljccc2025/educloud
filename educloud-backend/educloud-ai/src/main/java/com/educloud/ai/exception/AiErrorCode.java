package com.educloud.ai.exception;

import com.educloud.common.error.ErrorCode;

public enum AiErrorCode implements ErrorCode {
    AI_PROVIDER_UNAVAILABLE(503, "AI provider is unavailable"),
    AI_QUOTA_EXCEEDED(429, "Daily AI request quota exceeded"),
    AI_GLOBAL_BUDGET_EXCEEDED(429, "Global daily AI token budget exceeded"),
    AI_CONVERSATION_NOT_FOUND(404, "AI conversation not found"),
    AI_CONVERSATION_NOT_OWNED(403, "AI conversation does not belong to current user"),
    AI_STREAM_NOT_SUPPORTED(400, "Streaming responses are not supported in P1"),
    AI_QUESTION_TOO_LONG(400, "Question exceeds the 1000-character limit"),
    AI_ACCESS_DENIED(403, "AI assistant is available to students only");

    private final int httpStatus;
    private final String defaultMessage;

    AiErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
