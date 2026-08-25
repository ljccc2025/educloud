package com.educloud.live.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.enums.LiveRoomStatus;
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
@TableName("live_room")
public class LiveRoomEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("course_id")
    private Long courseId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("teacher_id")
    private Long teacherId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    @TableField("scheduled_end_at")
    private LocalDateTime scheduledEndAt;

    @TableField("status")
    private LiveRoomStatus status;

    @TableField("provider_type")
    private LiveProviderType providerType;

    @TableField("stream_key")
    private String streamKey;

    @TableField("allow_chat")
    private Boolean allowChat;

    @Version
    @TableField("version")
    private Long version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
