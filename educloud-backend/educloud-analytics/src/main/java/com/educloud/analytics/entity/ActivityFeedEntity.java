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

/**
 * 角色化动态流实体（规格：2026-08-27-activity-feed-certificate-design.md §3.1）。
 *
 * <p>由各业务服务领域事件经 {@code ActivityFeedConsumer} 消费写入，
 * {@code source_event} 唯一约束保证事件消费幂等。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_feed")
public class ActivityFeedEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 行为主体用户ID */
    @TableField("actor_id")
    private String actorId;

    /** STUDENT / TEACHER */
    @TableField("actor_role")
    private String actorRole;

    /** 动态类型，见规格 §4.1（ENROLLED / ASSIGNMENT_GRADED / COURSE_PUBLISHED ...） */
    @TableField("action_type")
    private String actionType;

    /** 目标类型：COURSE / ASSIGNMENT / CERTIFICATE */
    @TableField("target_type")
    private String targetType;

    /** 目标ID（课程ID/作业ID/证书ID） */
    @TableField("target_id")
    private String targetId;

    /** 目标标题（冗余，便于展示） */
    @TableField("target_title")
    private String targetTitle;

    /** 扩展字段 JSON：分数/进度/星级/评语 */
    @TableField("extra_json")
    private String extraJson;

    /** 来源事件ID（幂等，唯一约束；一个事件可派生多行时追加动作后缀） */
    @TableField("source_event")
    private String sourceEvent;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
