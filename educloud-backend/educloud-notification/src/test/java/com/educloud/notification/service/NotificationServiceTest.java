package com.educloud.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.notification.dto.request.PublishNotificationRequest;
import com.educloud.notification.dto.response.NotificationResponse;
import com.educloud.notification.dto.response.UnreadCountResponse;
import com.educloud.notification.entity.DeliveryTaskEntity;
import com.educloud.notification.entity.NotificationEntity;
import com.educloud.notification.entity.UserNotificationEntity;
import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.enums.TargetType;
import com.educloud.notification.exception.NotificationBizException;
import com.educloud.notification.mapper.DeliveryTaskMapper;
import com.educloud.notification.mapper.NotificationMapper;
import com.educloud.notification.mapper.UserNotificationMapper;
import com.educloud.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private UserNotificationMapper userNotificationMapper;

    @Mock
    private DeliveryTaskMapper deliveryTaskMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    @DisplayName("发布通知给指定用户测试")
    void testPublishNotificationToUsers() {
        // 邮件任务使用用户真实邮箱
        when(jdbcTemplate.queryForList(
                "SELECT email FROM educloud_user.sys_user WHERE id = ? LIMIT 1",
                String.class, 3001L)).thenReturn(List.of("student1@example.com"));
        when(jdbcTemplate.queryForList(
                "SELECT email FROM educloud_user.sys_user WHERE id = ? LIMIT 1",
                String.class, 3002L)).thenReturn(List.of("student2@example.com"));

        PublishNotificationRequest request = PublishNotificationRequest.builder()
                .title("作业截止提醒")
                .content("请于今晚提交第三章作业")
                .kind(NotificationKind.ASSIGNMENT)
                .targetType(TargetType.USER)
                .targetUserIds(List.of(3001L, 3002L))
                .actionLabel("查看作业")
                .actionPath("/assignments")
                .sendEmail(true)
                .build();

        NotificationResponse response = notificationService.publishNotification(9001L, request);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("作业截止提醒");
        verify(notificationMapper, times(1)).insert(any(NotificationEntity.class));
        verify(userNotificationMapper, times(2)).insert(any(UserNotificationEntity.class));
        verify(deliveryTaskMapper, times(2)).insert(any(DeliveryTaskEntity.class));
    }

    @Test
    @DisplayName("邮件投递任务使用用户真实邮箱测试")
    void testEmailDeliveryTaskUsesRealUserEmail() {
        when(jdbcTemplate.queryForList(
                "SELECT email FROM educloud_user.sys_user WHERE id = ? LIMIT 1",
                String.class, 3001L)).thenReturn(List.of("real.student@example.com"));

        PublishNotificationRequest request = PublishNotificationRequest.builder()
                .title("开课通知")
                .content("课程已开课")
                .kind(NotificationKind.COURSE)
                .targetType(TargetType.USER)
                .targetUserIds(List.of(3001L))
                .sendEmail(true)
                .build();

        notificationService.publishNotification(9001L, request);

        ArgumentCaptor<DeliveryTaskEntity> taskCaptor = ArgumentCaptor.forClass(DeliveryTaskEntity.class);
        verify(deliveryTaskMapper, times(1)).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getReceiverTarget()).isEqualTo("real.student@example.com");
        // 不再伪造虚构地址
        assertThat(taskCaptor.getValue().getReceiverTarget())
                .doesNotContain("@educloud.cn")
                .doesNotContain("user_");
    }

    @Test
    @DisplayName("用户无邮箱时跳过邮件投递任务测试")
    void testEmailDeliveryTaskSkippedWhenUserHasNoEmail() {
        when(jdbcTemplate.queryForList(
                "SELECT email FROM educloud_user.sys_user WHERE id = ? LIMIT 1",
                String.class, 3001L)).thenReturn(List.of());

        PublishNotificationRequest request = PublishNotificationRequest.builder()
                .title("开课通知")
                .content("课程已开课")
                .kind(NotificationKind.COURSE)
                .targetType(TargetType.USER)
                .targetUserIds(List.of(3001L))
                .sendEmail(true)
                .build();

        notificationService.publishNotification(9001L, request);

        // 站内信正常，但邮件任务被跳过
        verify(userNotificationMapper, times(1)).insert(any(UserNotificationEntity.class));
        verify(deliveryTaskMapper, never()).insert(any(DeliveryTaskEntity.class));
    }

    @Test
    @DisplayName("邮箱解析异常时跳过邮件投递任务测试")
    void testEmailDeliveryTaskSkippedWhenEmailResolutionFails() {
        when(jdbcTemplate.queryForList(
                "SELECT email FROM educloud_user.sys_user WHERE id = ? LIMIT 1",
                String.class, 3001L)).thenThrow(new RuntimeException("connection refused"));

        PublishNotificationRequest request = PublishNotificationRequest.builder()
                .title("开课通知")
                .content("课程已开课")
                .kind(NotificationKind.COURSE)
                .targetType(TargetType.USER)
                .targetUserIds(List.of(3001L))
                .sendEmail(true)
                .build();

        notificationService.publishNotification(9001L, request);

        verify(userNotificationMapper, times(1)).insert(any(UserNotificationEntity.class));
        verify(deliveryTaskMapper, never()).insert(any(DeliveryTaskEntity.class));
    }

    @Test
    @DisplayName("分页查询个人收件箱与未读数测试")
    void testGetMyNotificationsAndUnreadCount() {
        Long userId = 3001L;

        UserNotificationEntity un1 = UserNotificationEntity.builder()
                .id(101L)
                .userId(userId)
                .notificationId(501L)
                .isRead(0)
                .isDeleted(0)
                .createdAt(LocalDateTime.now())
                .build();

        NotificationEntity n1 = NotificationEntity.builder()
                .id(501L)
                .title("直播已开播")
                .content("高等数学直播中")
                .kind(NotificationKind.LIVE)
                .targetType(TargetType.USER)
                .actionLabel("进入直播")
                .actionPath("/live/1")
                .createdAt(LocalDateTime.now())
                .build();

        Page<UserNotificationEntity> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(un1));
        pageResult.setTotal(1L);

        when(userNotificationMapper.selectPage(any(), any())).thenReturn(pageResult);
        when(notificationMapper.selectBatchIds(Set.of(501L))).thenReturn(List.of(n1));

        PageResponse<NotificationResponse> result = notificationService.getMyNotifications(userId, 1, 10, null, false);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getTitle()).isEqualTo("直播已开播");
        assertThat(result.items().get(0).isRead()).isFalse();

        when(userNotificationMapper.countUnreadByUserId(userId)).thenReturn(1L);
        UnreadCountResponse unread = notificationService.getUnreadCount(userId);
        assertThat(unread.getUnreadCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("标记已读与越权拦截测试")
    void testMarkAsReadAndIdorProtection() {
        Long ownerId = 3001L;
        Long attackerId = 9999L;
        Long recordId = 101L;

        UserNotificationEntity record = UserNotificationEntity.builder()
                .id(recordId)
                .userId(ownerId)
                .notificationId(501L)
                .isRead(0)
                .isDeleted(0)
                .build();

        when(userNotificationMapper.selectById(recordId)).thenReturn(record);

        // 越权操作拦截
        assertThatThrownBy(() -> notificationService.markAsRead(attackerId, recordId))
                .isInstanceOf(BusinessException.class);

        // 本人正常标记
        notificationService.markAsRead(ownerId, recordId);
        assertThat(record.getIsRead()).isEqualTo(1);
        verify(userNotificationMapper, times(1)).updateById(record);
    }

    @Test
    @DisplayName("一键已读与逻辑删除测试")
    void testMarkAllAsReadAndDelete() {
        Long userId = 3001L;
        notificationService.markAllAsRead(userId);
        verify(userNotificationMapper, times(1)).markAllAsRead(eq(userId), any(LocalDateTime.class));

        UserNotificationEntity record = UserNotificationEntity.builder()
                .id(101L)
                .userId(userId)
                .notificationId(501L)
                .isDeleted(0)
                .build();

        when(userNotificationMapper.selectById(101L)).thenReturn(record);
        notificationService.deleteNotification(userId, 101L);
        assertThat(record.getIsDeleted()).isEqualTo(1);
        verify(userNotificationMapper, times(1)).updateById(record);
    }
}
