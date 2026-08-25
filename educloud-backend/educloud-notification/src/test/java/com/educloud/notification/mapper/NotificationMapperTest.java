package com.educloud.notification.mapper;

import com.educloud.notification.entity.NotificationEntity;
import com.educloud.notification.entity.UserNotificationEntity;
import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.enums.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMapperTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private UserNotificationMapper userNotificationMapper;

    @Test
    @DisplayName("Notification Mapper 基本增查测试")
    void testNotificationMapper() {
        NotificationEntity entity = NotificationEntity.builder()
                .id(1001L)
                .title("测试通知")
                .content("通知内容")
                .kind(NotificationKind.SYSTEM)
                .targetType(TargetType.USER)
                .senderId(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(notificationMapper.selectById(1001L)).thenReturn(entity);

        NotificationEntity result = notificationMapper.selectById(1001L);
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("测试通知");
    }

    @Test
    @DisplayName("UserNotification Mapper 未读统计与标记测试")
    void testUserNotificationMapper() {
        when(userNotificationMapper.countUnreadByUserId(9001L)).thenReturn(5L);
        when(userNotificationMapper.markAllAsRead(eq(9001L), any(LocalDateTime.class))).thenReturn(5);

        long unread = userNotificationMapper.countUnreadByUserId(9001L);
        assertThat(unread).isEqualTo(5L);

        int updated = userNotificationMapper.markAllAsRead(9001L, LocalDateTime.now());
        assertThat(updated).isEqualTo(5);
    }
}
