package com.educloud.search.entity;

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

/**
 * 搜索事件消费接收箱实体类（幂等与顺序保证）
 * 对应表：search_sync_inbox
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("search_sync_inbox")
public class SearchInboxEntity {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 消息全局唯一 ID */
    @TableField("message_id")
    private String messageId;

    /** 事件类型 */
    @TableField("event_type")
    private String eventType;

    /** 聚合根类型 */
    @TableField("aggregate_type")
    private String aggregateType;

    /** 聚合根 ID */
    @TableField("aggregate_id")
    private String aggregateId;

    /** 聚合根单调递增版本 */
    @TableField("aggregate_version")
    private Long aggregateVersion;

    /** 事件消息载荷 (JSON) */
    @TableField("payload")
    private String payload;

    /** 处理状态: PROCESSED / FAILED / IGNORED */
    @TableField("status")
    private String status;

    /** 失败原因 */
    @TableField("error_reason")
    private String errorReason;

    /** 接收时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
