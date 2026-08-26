package com.educloud.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.search.entity.IndexTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 索引重建任务 Mapper 接口
 */
@Mapper
public interface IndexTaskMapper extends BaseMapper<IndexTaskEntity> {
}
