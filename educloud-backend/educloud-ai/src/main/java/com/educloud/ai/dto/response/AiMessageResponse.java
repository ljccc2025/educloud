package com.educloud.ai.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiMessageResponse {
    private String id;
    private String role;
    private String content;
    /** OK / TRUNCATED / FAILED（FAILED 行仅审计，前端不渲染）。 */
    private String status;
    private LocalDateTime createdAt;
}
