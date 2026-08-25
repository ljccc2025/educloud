package com.educloud.live.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveRoomStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LiveRoomMapper extends BaseMapper<LiveRoomEntity> {

    @Update("""
        UPDATE live_room
        SET status = #{targetStatus},
            version = version + 1
        WHERE id = #{id}
          AND status = #{expectedStatus}
          AND deleted = 0
    """)
    int updateStatusCas(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus);

    @Update("""
        UPDATE live_room
        SET allow_chat = #{allowChat},
            version = version + 1
        WHERE id = #{id}
          AND deleted = 0
    """)
    int updateAllowChat(
            @Param("id") Long id,
            @Param("allowChat") Boolean allowChat);
}
