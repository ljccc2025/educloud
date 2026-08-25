package com.educloud.notification.support;

import com.educloud.notification.entity.DeliveryTaskEntity;
import com.educloud.notification.entity.NotificationEntity;
import com.educloud.notification.enums.DeliveryStatus;
import com.educloud.notification.mapper.DeliveryTaskMapper;
import com.educloud.notification.mapper.NotificationMapper;
import com.educloud.notification.spi.EmailChannelFactory;
import com.educloud.notification.spi.EmailChannelPlugin;
import com.educloud.notification.spi.model.EmailSendContext;
import com.educloud.notification.spi.model.EmailSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTaskJob {

    private final DeliveryTaskMapper deliveryTaskMapper;
    private final NotificationMapper notificationMapper;
    private final EmailChannelFactory emailChannelFactory;

    @Scheduled(fixedDelay = 10000)
    public void processPendingTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<DeliveryTaskEntity> pendingTasks = deliveryTaskMapper.selectPendingTasks(now, 50);
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return;
        }

        log.debug("[DeliveryTaskJob] Processing {} pending delivery tasks", pendingTasks.size());
        for (DeliveryTaskEntity task : pendingTasks) {
            processSingleTask(task, now);
        }
    }

    public void processSingleTask(DeliveryTaskEntity task, LocalDateTime now) {
        NotificationEntity notification = notificationMapper.selectById(task.getNotificationId());
        String subject = notification != null ? notification.getTitle() : "EduCloud 平台通知";
        String content = notification != null ? notification.getContent() : "您有一条新的通知";

        EmailSendContext context = EmailSendContext.builder()
                .to(task.getReceiverTarget())
                .subject(subject)
                .content(content)
                .html(false)
                .notificationId(task.getNotificationId())
                .userId(task.getUserId())
                .build();

        try {
            EmailChannelPlugin plugin = emailChannelFactory.getDefaultPlugin();
            EmailSendResult result = plugin.sendEmail(context);

            if (result.isSuccess()) {
                task.setStatus(DeliveryStatus.SUCCESS);
                task.setSentAt(now);
                task.setUpdatedAt(now);
                deliveryTaskMapper.updateById(task);
                log.info("[DeliveryTaskJob] Task {} dispatched successfully, msgId={}",
                        task.getId(), result.getMessageId());
            } else {
                handleTaskFailure(task, result.getErrorMessage(), now);
            }
        } catch (Exception e) {
            handleTaskFailure(task, e.getMessage(), now);
        }
    }

    private void handleTaskFailure(DeliveryTaskEntity task, String errorMsg, LocalDateTime now) {
        int newRetryCount = (task.getRetryCount() != null ? task.getRetryCount() : 0) + 1;
        task.setRetryCount(newRetryCount);
        task.setLastErrorMessage(sanitizeErrorMessage(errorMsg));
        task.setUpdatedAt(now);

        int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : 3;
        if (newRetryCount >= maxRetries) {
            task.setStatus(DeliveryStatus.FAILED);
            task.setNextRetryAt(null);
            log.warn("[DeliveryTaskJob] Task {} reached max retries ({}), marked as FAILED",
                    task.getId(), maxRetries);
        } else {
            // 指数退避: 1次重试 +1m, 2次重试 +5m, 3次重试 +15m
            long delayMinutes = (long) Math.pow(2, newRetryCount);
            task.setNextRetryAt(now.plusMinutes(delayMinutes));
            log.warn("[DeliveryTaskJob] Task {} failed (retry {}/{}), next attempt at {}",
                    task.getId(), newRetryCount, maxRetries, task.getNextRetryAt());
        }
        deliveryTaskMapper.updateById(task);
    }

    private String sanitizeErrorMessage(String error) {
        if (error == null) return "未知错误";
        if (error.length() > 200) {
            return error.substring(0, 200) + "...";
        }
        return error;
    }
}
