package com.educloud.analytics.service;

import com.educloud.analytics.entity.ActivityFeedEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 角色化动态流服务（规格 2026-08-27-activity-feed-certificate-design.md §5/§7）。
 *
 * <p>写入由 {@code ActivityFeedConsumer} 消费领域事件驱动，{@code sourceEvent}
 * 唯一约束保证幂等；查询按角色 + 当前登录用户过滤。</p>
 */
public interface ActivityFeedService {

    /**
     * 记录一条动态（幂等：相同 {@code sourceEvent} 重复调用不产生新行）。
     *
     * @param actorId      行为主体用户ID（必填）
     * @param actorRole    STUDENT / TEACHER（必填）
     * @param actionType   动态类型，见规格 §4.1（必填）
     * @param targetType   目标类型：COURSE / ASSIGNMENT / CERTIFICATE（可空）
     * @param targetId     目标ID（可空）
     * @param targetTitle  目标标题（可空）
     * @param extra        扩展字段，落库前序列化为 JSON（可空）
     * @param sourceEvent  来源事件幂等键（可空，为空时不做幂等约束）
     * @param occurredAt   事件发生时间（为空取当前时间）
     */
    void recordActivity(
            String actorId,
            String actorRole,
            String actionType,
            String targetType,
            String targetId,
            String targetTitle,
            Map<String, Object> extra,
            String sourceEvent,
            LocalDateTime occurredAt);

    /**
     * 按角色 + 用户查询动态（按发生时间倒序）。
     *
     * @param actorId   行为主体用户ID
     * @param actorRole STUDENT / TEACHER
     * @param limit     条数，内部钳制为 [1, 50]
     */
    List<ActivityFeedEntity> listActivities(String actorId, String actorRole, int limit);
}
