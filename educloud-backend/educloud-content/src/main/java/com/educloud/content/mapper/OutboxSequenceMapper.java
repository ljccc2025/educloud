package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.content.entity.OutboxSequenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxSequenceMapper extends BaseMapper<OutboxSequenceEntity> {
    @Select("SELECT `last_value` FROM outbox_sequence WHERE source_name = #{sourceName} FOR UPDATE")
    Long lockAndGetLastValue(@Param("sourceName") String sourceName);

    @Update("UPDATE outbox_sequence SET `last_value` = #{nextValue} WHERE source_name = #{sourceName}")
    int updateLastValue(@Param("sourceName") String sourceName, @Param("nextValue") Long nextValue);
}
