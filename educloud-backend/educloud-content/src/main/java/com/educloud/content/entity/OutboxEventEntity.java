package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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

    private LocalDateTime occurredAt;

    private Long sourceSequence;

    private String publishStatus;

    private Integer attemptCount;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime publishedAt;

    private LocalDateTime archivedAt;
}
