package com.educloud.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Outbox 事件（outbox_event）。业务事务内写入，发布器投递 RabbitMQ 后标记已发布（数据设计第 14 节）。
 */
@Data
@TableName("outbox_event")
public class OutboxEventEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventId;

    private String aggregateType;

    private String aggregateId;

    private String eventType;

    private Integer eventVersion;

    private Long aggregateVersion;

    private String payloadJson;

    private String requestId;

    private String traceId;

    private Instant occurredAt;

    private Long sourceSequence;

    /** PENDING/PUBLISHED/FAILED。 */
    private String publishStatus;

    private Integer attemptCount;

    private Instant nextAttemptAt;

    private Instant publishedAt;

    private Instant archivedAt;
}
