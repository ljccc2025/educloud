package com.educloud.notification.service;

import com.educloud.common.api.PageResponse;
import com.educloud.notification.dto.request.PublishNotificationRequest;
import com.educloud.notification.dto.response.NotificationResponse;
import com.educloud.notification.dto.response.UnreadCountResponse;
import com.educloud.notification.enums.NotificationKind;

public interface NotificationService {

    NotificationResponse publishNotification(Long senderId, PublishNotificationRequest request);

    PageResponse<NotificationResponse> getMyNotifications(Long userId, int page, int size, NotificationKind kind, Boolean unreadOnly);

    UnreadCountResponse getUnreadCount(Long userId);

    void markAsRead(Long userId, Long userNotificationId);

    void markAllAsRead(Long userId);

    void deleteNotification(Long userId, Long userNotificationId);

    void sendDirectNotification(Long targetUserId, NotificationKind kind, String title, String content, String actionLabel, String actionPath, boolean sendEmail);
}
