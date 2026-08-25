package com.educloud.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.live.enums.LiveReplayStatus;
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
@TableName("live_replay")
public class LiveReplayEntity {

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
    @TableField("file_id")
    private Long fileId;

    @TableField("title")
    private String title;

    @TableField("duration_seconds")
    private Long durationSeconds;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("status")
    private LiveReplayStatus status;

    @TableField("available_at")
    private LocalDateTime availableAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
