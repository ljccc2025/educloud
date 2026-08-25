package com.educloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.order.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {

    /** relay 投递成功：CAS 置 PUBLISHED（防并发 relay 双发）。 */
    @Update("UPDATE outbox_event SET publish_status = 'PUBLISHED', published_at = NOW(3) "
            + "WHERE id = #{id} AND publish_status = 'PENDING'")
    int markPublished(@Param("id") Long id);

    /** relay 投递失败：attempt_count+1 并按退避时间推迟下次重试。 */
    @Update("UPDATE outbox_event SET attempt_count = attempt_count + 1, next_attempt_at = #{nextAttemptAt} "
            + "WHERE id = #{id} AND publish_status = 'PENDING'")
    int markFailedAttempt(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
