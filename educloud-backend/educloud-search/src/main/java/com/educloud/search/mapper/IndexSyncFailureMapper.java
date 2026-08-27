package com.educloud.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.search.entity.IndexSyncFailureEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 索引同步死信失败记录 Mapper 接口
 */
@Mapper
public interface IndexSyncFailureMapper extends BaseMapper<IndexSyncFailureEntity> {
}
