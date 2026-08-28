package com.educloud.ai.provider;

/** 上游模型调用失败。retryable=true 表示"连接失败或上游 429/5xx"（可重试一次），超时与其他 4xx 不可重试。 */
public class AiProviderException extends RuntimeException {

    private final int upstreamStatus;
    private final boolean retryable;

    public AiProviderException(String message, int upstreamStatus, boolean retryable, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.retryable = retryable;
    }

    public int upstreamStatus() {
        return upstreamStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
