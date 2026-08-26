package com.educloud.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.notification.dto.request.PublishNotificationRequest;
import com.educloud.notification.dto.response.NotificationResponse;
import com.educloud.notification.dto.response.UnreadCountResponse;
import com.educloud.notification.entity.DeliveryTaskEntity;
import com.educloud.notification.entity.NotificationEntity;
import com.educloud.notification.entity.UserNotificationEntity;
import com.educloud.notification.enums.ChannelCode;
import com.educloud.notification.enums.DeliveryStatus;
import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.enums.TargetType;
import com.educloud.notification.exception.NotificationBizException;
import com.educloud.notification.exception.NotificationErrorCode;
import com.educloud.notification.mapper.DeliveryTaskMapper;
import com.educloud.notification.mapper.NotificationMapper;
import com.educloud.notification.mapper.UserNotificationMapper;
import com.educloud.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final DeliveryTaskMapper deliveryTaskMapper;

    // 默认活跃测试用户种子（全员广播分发受众）
    private static final List<Long> DEMO_BROADCAST_USERS = List.of(
            9000000000000000001L, // demo_teacher
            9000000000000000002L, // demo_admin
            2091648316809035778L, // fe_demo_10
            2091671876361404417L  // admin
    );

    @Override
    @Transactional
    public NotificationResponse publishNotification(Long senderId, PublishNotificationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Long notificationId = IdWorker.getId();

        NotificationEntity notification = NotificationEntity.builder()
                .id(notificationId)
                .title(request.getTitle())
                .content(request.getContent())
                .kind(request.getKind())
                .targetType(request.getTargetType())
                .senderId(senderId != null ? senderId : 0L)
                .actionLabel(request.getActionLabel())
                .actionPath(request.getActionPath())
                .createdAt(now)
                .updatedAt(now)
                .build();
        notificationMapper.insert(notification);

        List<Long> recipientUserIds = new ArrayList<>();
        if (request.getTargetType() == TargetType.ALL) {
            recipientUserIds.addAll(DEMO_BROADCAST_USERS);
        } else if (request.getTargetUserIds() != null && !request.getTargetUserIds().isEmpty()) {
            recipientUserIds.addAll(request.getTargetUserIds());
        }

        for (Long uid : recipientUserIds) {
            saveUserNotification(uid, notificationId, notification.getKind(), now);
            if (request.isSendEmail()) {
                saveEmailDeliveryTask(uid, notificationId, now);
            }
        }

        return NotificationResponse.builder()
                .id(notificationId)
                .notificationId(notificationId)
                .title(notification.getTitle())
                .content(notification.getContent())
                .kind(notification.getKind())
                .targetType(notification.getTargetType())
                .senderId(notification.getSenderId())
                .actionLabel(notification.getActionLabel())
                .actionPath(notification.getActionPath())
                .read(false)
                .readAt(null)
                .createdAt(now)
                .build();
    }

    private void saveUserNotification(Long userId, Long notificationId, NotificationKind kind, LocalDateTime now) {
        UserNotificationEntity userNotif = UserNotificationEntity.builder()
                .id(IdWorker.getId())
                .userId(userId)
                .notificationId(notificationId)
                .kind(kind)
                .isRead(0)
                .readAt(null)
                .isDeleted(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userNotificationMapper.insert(userNotif);
    }

    private void saveEmailDeliveryTask(Long userId, Long notificationId, LocalDateTime now) {
        DeliveryTaskEntity task = DeliveryTaskEntity.builder()
                .id(IdWorker.getId())
                .notificationId(notificationId)
                .userId(userId)
                .channelCode(ChannelCode.EMAIL)
                .receiverTarget("user_" + userId + "@educloud.cn")
                .status(DeliveryStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        deliveryTaskMapper.insert(task);
    }

    @Override
    public PageResponse<NotificationResponse> getMyNotifications(
            Long userId, int page, int size, NotificationKind kind, Boolean unreadOnly) {
        if (userId == null) {
            return PageResponse.of(Collections.emptyList(), Math.max(1, page), Math.max(1, size), 0);
        }

        int current = Math.max(1, page);
        // 分页钳制：防止恶意超大 size 触发全量拉取（与 BUG-012 同型分页 DoS）
        int pageSize = Math.min(Math.max(1, size), 100);

        LambdaQueryWrapper<UserNotificationEntity> query = new LambdaQueryWrapper<UserNotificationEntity>()
                .eq(UserNotificationEntity::getUserId, userId)
                .eq(UserNotificationEntity::getIsDeleted, 0);

        if (kind != null) {
            query.eq(UserNotificationEntity::getKind, kind);
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            query.eq(UserNotificationEntity::getIsRead, 0);
        }
        query.orderByDesc(UserNotificationEntity::getCreatedAt);

        Page<UserNotificationEntity> p = new Page<>(current, pageSize);
        Page<UserNotificationEntity> pageResult = userNotificationMapper.selectPage(p, query);

        List<UserNotificationEntity> records = pageResult.getRecords();
        if (records.isEmpty()) {
            return PageResponse.of(Collections.emptyList(), current, pageSize, pageResult.getTotal());
        }

        Set<Long> notifIds = records.stream()
                .map(UserNotificationEntity::getNotificationId)
                .collect(Collectors.toSet());

        List<NotificationEntity> notifEntities = notificationMapper.selectBatchIds(notifIds);
        Map<Long, NotificationEntity> notifMap = notifEntities.stream()
                .collect(Collectors.toMap(NotificationEntity::getId, n -> n, (k1, k2) -> k1));

        List<NotificationResponse> responses = new ArrayList<>();
        for (UserNotificationEntity record : records) {
            NotificationEntity parent = notifMap.get(record.getNotificationId());
            if (parent == null) continue;

            responses.add(NotificationResponse.builder()
                    .id(record.getId())
                    .notificationId(parent.getId())
                    .title(parent.getTitle())
                    .content(parent.getContent())
                    .kind(parent.getKind())
                    .targetType(parent.getTargetType())
                    .senderId(parent.getSenderId())
                    .actionLabel(parent.getActionLabel())
                    .actionPath(parent.getActionPath())
                    .read(record.getIsRead() != null && record.getIsRead() == 1)
                    .readAt(record.getReadAt())
                    .createdAt(record.getCreatedAt())
                    .build());
        }

        return PageResponse.of(responses, current, pageSize, pageResult.getTotal());
    }

    @Override
    public UnreadCountResponse getUnreadCount(Long userId) {
        if (userId == null) {
            return new UnreadCountResponse(0L);
        }
        long count = userNotificationMapper.countUnreadByUserId(userId);
        return new UnreadCountResponse(count);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long userNotificationId) {
        UserNotificationEntity record = userNotificationMapper.selectById(userNotificationId);
        if (record == null || (record.getIsDeleted() != null && record.getIsDeleted() == 1)) {
            throw new NotificationBizException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if (userId != null && !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "无权操作他人的通知");
        }
        if (record.getIsRead() == null || record.getIsRead() == 0) {
            LocalDateTime now = LocalDateTime.now();
            record.setIsRead(1);
            record.setReadAt(now);
            record.setUpdatedAt(now);
            userNotificationMapper.updateById(record);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        if (userId == null) return;
        userNotificationMapper.markAllAsRead(userId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void deleteNotification(Long userId, Long userNotificationId) {
        UserNotificationEntity record = userNotificationMapper.selectById(userNotificationId);
        if (record == null || (record.getIsDeleted() != null && record.getIsDeleted() == 1)) {
            throw new NotificationBizException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if (userId != null && !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "无权删除他人的通知");
        }
        LocalDateTime now = LocalDateTime.now();
        record.setIsDeleted(1);
        record.setUpdatedAt(now);
        userNotificationMapper.updateById(record);
    }

    @Override
    @Transactional
    public void sendDirectNotification(
            Long targetUserId, NotificationKind kind, String title, String content,
            String actionLabel, String actionPath, boolean sendEmail) {
        if (targetUserId == null) {
            throw new NotificationBizException(NotificationErrorCode.INVALID_TARGET_USER);
        }
        LocalDateTime now = LocalDateTime.now();
        Long notificationId = IdWorker.getId();

        NotificationEntity notification = NotificationEntity.builder()
                .id(notificationId)
                .title(title)
                .content(content)
                .kind(kind != null ? kind : NotificationKind.SYSTEM)
                .targetType(TargetType.USER)
                .senderId(0L)
                .actionLabel(actionLabel)
                .actionPath(actionPath)
                .createdAt(now)
                .updatedAt(now)
                .build();
        notificationMapper.insert(notification);

        saveUserNotification(targetUserId, notificationId, notification.getKind(), now);

        if (sendEmail) {
            saveEmailDeliveryTask(targetUserId, notificationId, now);
        }
    }
}
