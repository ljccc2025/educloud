package com.educloud.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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

    /** 认领实例标识（V003 迁移新增，仅认领实例可取回投递）。 */
    private String claimOwner;

    private LocalDateTime publishedAt;

    private LocalDateTime archivedAt;
}
