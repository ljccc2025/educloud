package com.educloud.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.payment.enums.OutboxStatus;
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
@TableName("payment_outbox_event")
public class PaymentOutboxEventEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("aggregate_type")
    private String aggregateType;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("aggregate_id")
    private Long aggregateId;

    @TableField("event_type")
    private String eventType;

    @TableField("payload")
    private String payload;

    @TableField("status")
    private OutboxStatus status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
