package com.educloud.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 角色化动态流查询返回项（规格 2026-08-27-activity-feed-certificate-design.md §7）。
 *
 * <p>{@code timestamp} 返回 ISO-8601 格式（如 2026-08-27T10:30:00），
 * 前端据此计算相对时间，避免 Invalid Date（沿用既有修复经验）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityItem {

    /** 动态ID */
    private String id;

    /** 动态类型（ENROLLED / ASSIGNMENT_GRADED / COURSE_PUBLISHED ...） */
    private String actionType;

    /** 动作中文文案（按规格 §4.1 模板组合目标标题与扩展字段） */
    private String action;

    /** 目标类型：COURSE / ASSIGNMENT / CERTIFICATE */
    private String targetType;

    /** 目标ID */
    private String targetId;

    /** 目标标题 */
    private String targetTitle;

    /** 扩展字段（分数/进度/星级/评语） */
    private Map<String, Object> extra;

    /** 事件发生时间（ISO-8601） */
    private String timestamp;
}
