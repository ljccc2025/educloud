package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.OutboxSequenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Outbox 水位数据访问（outbox_sequence）。单条原子 UPDATE 保证 source_sequence 单调（数据设计第 14 节）。 */
@Mapper
public interface OutboxSequenceMapper extends BaseMapper<OutboxSequenceEntity> {

    @Update("UPDATE outbox_sequence SET `last_value` = `last_value` + 1 WHERE source_name = #{sourceName}")
    int increment(@Param("sourceName") String sourceName);

    @Select("SELECT `last_value` FROM outbox_sequence WHERE source_name = #{sourceName}")
    Long selectValue(@Param("sourceName") String sourceName);
}
