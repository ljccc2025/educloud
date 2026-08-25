package com.educloud.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.notification.entity.UserNotificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotificationEntity> {

    @Select("SELECT COUNT(1) FROM sys_user_notification WHERE user_id = #{userId} AND is_deleted = 0 AND is_read = 0")
    long countUnreadByUserId(@Param("userId") Long userId);

    @Update("UPDATE sys_user_notification SET is_read = 1, read_at = #{readAt}, updated_at = #{readAt} WHERE user_id = #{userId} AND is_deleted = 0 AND is_read = 0")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
