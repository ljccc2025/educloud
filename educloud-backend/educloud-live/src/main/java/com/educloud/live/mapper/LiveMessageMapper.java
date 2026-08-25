package com.educloud.live.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.live.entity.LiveMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface LiveMessageMapper extends BaseMapper<LiveMessageEntity> {

    @Update("""
        UPDATE live_message
        SET status = 'RECALLED',
            recalled_at = #{recalledAt},
            recalled_by = #{recalledBy}
        WHERE id = #{id}
          AND status = 'NORMAL'
    """)
    int recallMessage(
            @Param("id") Long id,
            @Param("recalledAt") LocalDateTime recalledAt,
            @Param("recalledBy") Long recalledBy);
}
