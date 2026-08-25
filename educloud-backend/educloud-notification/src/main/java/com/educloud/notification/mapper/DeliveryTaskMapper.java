package com.educloud.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.notification.entity.DeliveryTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeliveryTaskMapper extends BaseMapper<DeliveryTaskEntity> {

    @Select("SELECT * FROM sys_delivery_task WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= #{now}) ORDER BY id ASC LIMIT #{limit}")
    List<DeliveryTaskEntity> selectPendingTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
