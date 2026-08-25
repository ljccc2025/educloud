package com.educloud.notification.support;

import com.educloud.notification.entity.DeliveryTaskEntity;
import com.educloud.notification.entity.NotificationEntity;
import com.educloud.notification.enums.ChannelCode;
import com.educloud.notification.enums.DeliveryStatus;
import com.educloud.notification.mapper.DeliveryTaskMapper;
import com.educloud.notification.mapper.NotificationMapper;
import com.educloud.notification.spi.EmailChannelFactory;
import com.educloud.notification.spi.EmailChannelPlugin;
import com.educloud.notification.spi.model.EmailSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryTaskJobTest {

    @Mock
    private DeliveryTaskMapper deliveryTaskMapper;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private EmailChannelFactory emailChannelFactory;

    @Mock
    private EmailChannelPlugin emailChannelPlugin;

    @InjectMocks
    private DeliveryTaskJob deliveryTaskJob;

    @Test
    @DisplayName("处理待发送任务成功测试")
    void testProcessTaskSuccess() {
        LocalDateTime now = LocalDateTime.now();
        DeliveryTaskEntity task = DeliveryTaskEntity.builder()
                .id(7001L)
                .notificationId(5001L)
                .userId(3001L)
                .channelCode(ChannelCode.EMAIL)
                .receiverTarget("student@educloud.cn")
                .status(DeliveryStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .build();

        NotificationEntity notif = NotificationEntity.builder()
                .id(5001L)
                .title("开课通知")
                .content("课程已开课")
                .build();

        when(deliveryTaskMapper.selectPendingTasks(any(), eq(50))).thenReturn(List.of(task));
        when(notificationMapper.selectById(5001L)).thenReturn(notif);
        when(emailChannelFactory.getDefaultPlugin()).thenReturn(emailChannelPlugin);
        when(emailChannelPlugin.sendEmail(any())).thenReturn(EmailSendResult.success("MSG_123"));

        deliveryTaskJob.processPendingTasks();

        assertThat(task.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(task.getSentAt()).isNotNull();
        verify(deliveryTaskMapper, times(1)).updateById(task);
    }

    @Test
    @DisplayName("处理待发送任务失败指数退避与最终失败测试")
    void testProcessTaskFailureAndBackoff() {
        LocalDateTime now = LocalDateTime.now();
        DeliveryTaskEntity task = DeliveryTaskEntity.builder()
                .id(7002L)
                .notificationId(5002L)
                .userId(3001L)
                .channelCode(ChannelCode.EMAIL)
                .receiverTarget("student@educloud.cn")
                .status(DeliveryStatus.PENDING)
                .retryCount(2) // 已经是第2次
                .maxRetries(3)
                .build();

        when(emailChannelFactory.getDefaultPlugin()).thenReturn(emailChannelPlugin);
        when(emailChannelPlugin.sendEmail(any())).thenReturn(EmailSendResult.failed("SMTP 连接超时"));

        deliveryTaskJob.processSingleTask(task, now);

        // 达到最大重试次数 3，标记为 FAILED
        assertThat(task.getRetryCount()).isEqualTo(3);
        assertThat(task.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(task.getLastErrorMessage()).contains("SMTP 连接超时");
        verify(deliveryTaskMapper, times(1)).updateById(task);
    }
}
