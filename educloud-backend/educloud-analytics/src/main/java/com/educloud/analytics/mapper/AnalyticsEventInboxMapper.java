package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.AnalyticsEventInboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnalyticsEventInboxMapper extends BaseMapper<AnalyticsEventInboxEntity> {

    int insertIfNotExists(@Param("entity") AnalyticsEventInboxEntity entity);
}
