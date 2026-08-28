package com.educloud.ai.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiChatResponse {
    private String conversationId;
    private String messageId;
    private String content;
    private String finishReason;
    private AiUsageResponse usage;
    private boolean degraded;
}
