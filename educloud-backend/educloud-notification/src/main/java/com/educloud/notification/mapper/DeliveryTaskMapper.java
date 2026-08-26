package com.educloud.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.notification.entity.DeliveryTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeliveryTaskMapper extends BaseMapper<DeliveryTaskEntity> {

    /**
     * 选取待投递任务：PENDING 且到期，或 SENDING 但超过 5 分钟未收敛（实例崩溃后的认领恢复）。
     * 多实例部署时配合 claimTask 的 CAS 认领，避免同一任务被重复发送。
     */
    @Select("""
        SELECT * FROM sys_delivery_task
        WHERE (status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
           OR (status = 'SENDING' AND updated_at < DATE_SUB(#{now}, INTERVAL 5 MINUTE))
        ORDER BY id ASC LIMIT #{limit}
        """)
    List<DeliveryTaskEntity> selectPendingTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * CAS 认领任务：仅 PENDING 或已逾期的 SENDING 可被认领，认领后置 SENDING 并刷新 updated_at。
     *
     * @return 认领成功行数（0 = 已被其他实例处理）
     */
    @Update("""
        UPDATE sys_delivery_task
        SET status = 'SENDING', updated_at = #{now}
        WHERE id = #{id}
          AND (status = 'PENDING' OR (status = 'SENDING' AND updated_at < DATE_SUB(#{now}, INTERVAL 5 MINUTE)))
        """)
    int claimTask(@Param("id") Long id, @Param("now") LocalDateTime now);
}
