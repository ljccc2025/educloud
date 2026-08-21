package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Inbox 事件（inbox_event）。M03 暂无上游事件源，建表作为技术模板（设计规格第 9 节）。
 */
@Data
@TableName("inbox_event")
public class InboxEventEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventId;

    private String eventType;

    private String sourceService;

    private Integer eventVersion;

    private Long sourceSequence;

    private String aggregateType;

    private String aggregateId;

    private Long aggregateVersion;

    private String processStatus;

    private String businessEffect;

    private Instant receivedAt;

    private Instant processedAt;

    private String errorCode;
}
