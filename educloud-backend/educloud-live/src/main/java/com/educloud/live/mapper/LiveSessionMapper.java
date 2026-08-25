package com.educloud.live.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.live.entity.LiveSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface LiveSessionMapper extends BaseMapper<LiveSessionEntity> {

    @Update("""
        UPDATE live_session
        SET status = 'ENDED',
            ended_at = #{endedAt},
            ended_by = #{endedBy},
            peak_viewers = #{peakViewers},
            total_viewers = #{totalViewers}
        WHERE id = #{id}
          AND status = 'LIVING'
          AND deleted = 0
    """)
    int endSessionCas(
            @Param("id") Long id,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("endedBy") Long endedBy,
            @Param("peakViewers") Integer peakViewers,
            @Param("totalViewers") Integer totalViewers);

    @Update("""
        UPDATE live_session
        SET peak_viewers = GREATEST(peak_viewers, #{currentViewers})
        WHERE id = #{id}
          AND status = 'LIVING'
          AND deleted = 0
    """)
    int updatePeakViewers(
            @Param("id") Long id,
            @Param("currentViewers") Integer currentViewers);
}
