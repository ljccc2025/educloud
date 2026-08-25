package com.educloud.live.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.live.entity.LiveAttendanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface LiveAttendanceMapper extends BaseMapper<LiveAttendanceEntity> {

    @Update("""
        UPDATE live_attendance
        SET last_active_at = #{now},
            watched_seconds = watched_seconds + #{deltaSeconds}
        WHERE session_id = #{sessionId}
          AND student_id = #{studentId}
    """)
    int heartbeat(
            @Param("sessionId") Long sessionId,
            @Param("studentId") Long studentId,
            @Param("now") LocalDateTime now,
            @Param("deltaSeconds") long deltaSeconds);

    @Select("""
        SELECT COUNT(DISTINCT student_id)
        FROM live_attendance
        WHERE session_id = #{sessionId}
    """)
    int countTotalViewers(@Param("sessionId") Long sessionId);
}
