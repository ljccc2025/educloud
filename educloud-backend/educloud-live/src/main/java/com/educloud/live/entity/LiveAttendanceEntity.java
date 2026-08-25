package com.educloud.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("live_attendance")
public class LiveAttendanceEntity {

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
    @TableField("student_id")
    private Long studentId;

    @TableField("joined_at")
    private LocalDateTime joinedAt;

    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;

    @TableField("left_at")
    private LocalDateTime leftAt;

    @TableField("watched_seconds")
    private Long watchedSeconds;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
