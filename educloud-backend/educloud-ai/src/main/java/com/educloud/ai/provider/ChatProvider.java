package com.educloud.ai.provider;

import com.educloud.ai.chat.ChatTurn;

import java.util.List;

/** LLM 供应商 SPI；P1 唯一实现 OpenAiCompatibleProvider，P2/P3 换模型只换实现。 */
public interface ChatProvider {

    ChatResult chat(List<ChatTurn> messages, ChatOptions options);

    record ChatOptions(int maxTokens) {
    }

    /** latencyMs 供 ai_message.latency_ms 审计列使用（V001 DDL）。 */
    record ChatResult(
            String content,
            String finishReason,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            String model,
            long latencyMs) {
    }
}
