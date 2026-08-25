package com.educloud.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.live.enums.LiveMessageStatus;
import com.educloud.live.enums.LiveMessageType;
import com.educloud.live.enums.LiveSenderRole;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("live_message")
public class LiveMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("room_id")
    private Long roomId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("session_id")
    private Long sessionId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("sender_id")
    private Long senderId;

    @TableField("sender_name")
    private String senderName;

    @TableField("sender_role")
    private LiveSenderRole senderRole;

    @TableField("message_type")
    private LiveMessageType messageType;

    @TableField("content")
    private String content;

    @TableField("status")
    private LiveMessageStatus status;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("recalled_at")
    private LocalDateTime recalledAt;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("recalled_by")
    private Long recalledBy;
}
