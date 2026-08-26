package com.educloud.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("analytics_event_inbox")
public class AnalyticsEventInboxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField("source_service")
    private String sourceService;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
