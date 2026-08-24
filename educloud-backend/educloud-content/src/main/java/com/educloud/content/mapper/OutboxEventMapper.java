package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.content.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {
}
