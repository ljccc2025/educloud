package com.educloud.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.search.entity.SearchInboxEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 搜索事件消费接收箱 Mapper 接口
 */
@Mapper
public interface SearchInboxMapper extends BaseMapper<SearchInboxEntity> {
}
