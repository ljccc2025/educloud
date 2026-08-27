package com.educloud.analytics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.analytics.entity.ActivityFeedEntity;
import com.educloud.analytics.mapper.ActivityFeedMapper;
import com.educloud.analytics.service.ActivityFeedService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 角色化动态流服务实现：写入走 {@link ActivityFeedMapper#insertIdempotent}
 * （source_event 唯一约束幂等），查询走 MyBatis-Plus LambdaQueryWrapper。
 *
 * <p>容错：写入/序列化异常仅记日志不抛出，避免阻断事件消费链路（规格 §9）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityFeedServiceImpl implements ActivityFeedService {

    /** 单次查询上限（规格 §7：默认 10，上限 50）。 */
    public static final int MAX_LIMIT = 50;

    private final ActivityFeedMapper activityFeedMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void recordActivity(
            String actorId,
            String actorRole,
            String actionType,
            String targetType,
            String targetId,
            String targetTitle,
            Map<String, Object> extra,
            String sourceEvent,
            LocalDateTime occurredAt) {
        if (!StringUtils.hasText(actorId) || !StringUtils.hasText(actionType)) {
            log.warn("Skip activity with blank actorId or actionType: actorId={}, actionType={}, sourceEvent={}",
                    actorId, actionType, sourceEvent);
            return;
        }

        String extraJson = null;
        if (extra != null && !extra.isEmpty()) {
            try {
                extraJson = objectMapper.writeValueAsString(extra);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize activity extra to JSON, extra dropped: actionType={}, sourceEvent={}",
                        actionType, sourceEvent, e);
            }
        }

        ActivityFeedEntity entity = ActivityFeedEntity.builder()
                .actorId(actorId.trim())
                .actorRole(StringUtils.hasText(actorRole) ? actorRole.trim() : "STUDENT")
                .actionType(actionType.trim())
                .targetType(targetType)
                .targetId(targetId)
                .targetTitle(targetTitle)
                .extraJson(extraJson)
                .sourceEvent(StringUtils.hasText(sourceEvent) ? sourceEvent.trim() : null)
                .occurredAt(occurredAt != null ? occurredAt : LocalDateTime.now())
                .build();

        try {
            int rows = activityFeedMapper.insertIdempotent(entity);
            if (rows <= 0) {
                log.info("Duplicate activity ignored (source_event exists): sourceEvent={}, actionType={}",
                        sourceEvent, actionType);
            } else {
                log.info("Recorded activity: actor={}, role={}, action={}, target={}:{}, sourceEvent={}",
                        actorId, entity.getActorRole(), actionType, targetType, targetId, sourceEvent);
            }
        } catch (Exception e) {
            log.error("Failed to insert activity feed row: actor={}, actionType={}, sourceEvent={}",
                    actorId, actionType, sourceEvent, e);
        }
    }

    @Override
    public List<ActivityFeedEntity> listActivities(String actorId, String actorRole, int limit) {
        if (!StringUtils.hasText(actorId) || !StringUtils.hasText(actorRole)) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        try {
            LambdaQueryWrapper<ActivityFeedEntity> wrapper = new LambdaQueryWrapper<ActivityFeedEntity>()
                    .eq(ActivityFeedEntity::getActorId, actorId.trim())
                    .eq(ActivityFeedEntity::getActorRole, actorRole.trim())
                    .orderByDesc(ActivityFeedEntity::getOccurredAt)
                    .last("LIMIT " + safeLimit);
            List<ActivityFeedEntity> list = activityFeedMapper.selectList(wrapper);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            // 查询降级：失败返回空数组，前端显示空状态，不影响首页/工作台其他部分（规格 §9）
            log.error("Failed to query activity feed: actor={}, role={}", actorId, actorRole, e);
            return Collections.emptyList();
        }
    }
}
