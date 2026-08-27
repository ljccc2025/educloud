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
 * 索引同步死信失败记录实体类
 * 对应表：index_sync_failure（DLQ 死信落库，供定时/手动重放）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("index_sync_failure")
public class IndexSyncFailureEntity {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 消息全局唯一 ID */
    @TableField("message_id")
    private String messageId;

    /** 原始交换机（死信发生前所在交换机） */
    @TableField("exchange")
    private String exchange;

    /** 原始路由键（死信发生前所在路由键） */
    @TableField("routing_key")
    private String routingKey;

    /** 死信消息载荷 (JSON) */
    @TableField("payload")
    private String payload;

    /** 失败原因 */
    @TableField("error")
    private String error;

    /** 已重试次数（重放失败累加，达到上限转 DEAD） */
    @TableField("retry_count")
    private Integer retryCount;

    /** 状态: PENDING / RESOLVED / DEAD */
    @TableField("status")
    private String status;

    /** 死信产生时间 */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
