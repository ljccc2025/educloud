package com.educloud.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_conversation")
public class AiConversationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long studentId;
    private String title;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
