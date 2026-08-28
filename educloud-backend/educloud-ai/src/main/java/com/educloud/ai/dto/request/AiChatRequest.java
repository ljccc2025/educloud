package com.educloud.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    /** 省略时服务端新建会话并返回其 id。 */
    private Long conversationId;
    @NotBlank
    private String question;
    /** P1 恒拒：true 时返回 400 AI_STREAM_NOT_SUPPORTED（不静默降级）。 */
    private Boolean stream;
}
