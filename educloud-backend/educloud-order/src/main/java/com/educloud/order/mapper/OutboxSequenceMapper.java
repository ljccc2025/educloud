package com.educloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.order.entity.OutboxSequenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxSequenceMapper extends BaseMapper<OutboxSequenceEntity> {

    @Update("UPDATE outbox_sequence SET `last_value` = `last_value` + 1 WHERE source_name = #{sourceName}")
    int increment(@Param("sourceName") String sourceName);

    @Select("SELECT `last_value` FROM outbox_sequence WHERE source_name = #{sourceName}")
    Long selectValue(@Param("sourceName") String sourceName);
}
