package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.ActivityFeedEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色化动态流 Mapper。
 *
 * <p>幂等插入依赖 {@code activity_feed.uk_source_event} 唯一约束：
 * {@code ON DUPLICATE KEY UPDATE id = id} 使重复事件不落新行
 * （此时 MySQL affected rows 返回 0）。</p>
 */
@Mapper
public interface ActivityFeedMapper extends BaseMapper<ActivityFeedEntity> {

    @Insert("INSERT INTO activity_feed (actor_id, actor_role, action_type, target_type, target_id, target_title, extra_json, source_event, occurred_at) "
          + "VALUES (#{actorId}, #{actorRole}, #{actionType}, #{targetType}, #{targetId}, #{targetTitle}, #{extraJson}, #{sourceEvent}, #{occurredAt}) "
          + "ON DUPLICATE KEY UPDATE id = id")
    int insertIdempotent(ActivityFeedEntity entity);
}
