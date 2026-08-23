package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Mapper;

/** Outbox 事件数据访问（OutboxEventEntity，V000 表）。 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {
}
