package com.educloud.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class AiMessageEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String provider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer latencyMs;
    private String finishReason;
    private String status;
    private String errorCode;
    private LocalDateTime createdAt;
}
