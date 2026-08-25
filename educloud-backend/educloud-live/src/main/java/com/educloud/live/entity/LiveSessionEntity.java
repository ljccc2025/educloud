package com.educloud.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.live.enums.LiveSessionStatus;
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
@TableName("live_session")
public class LiveSessionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("room_id")
    private Long roomId;

    @TableField("session_no")
    private Integer sessionNo;

    @TableField("status")
    private LiveSessionStatus status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("started_by")
    private Long startedBy;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("ended_by")
    private Long endedBy;

    @TableField("peak_viewers")
    private Integer peakViewers;

    @TableField("total_viewers")
    private Integer totalViewers;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
