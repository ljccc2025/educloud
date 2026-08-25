package com.educloud.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.notification.enums.ChannelCode;
import com.educloud.notification.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_delivery_task")
public class DeliveryTaskEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long notificationId;

    private Long userId;

    private ChannelCode channelCode;

    private String receiverTarget;

    private DeliveryStatus status;

    private Integer retryCount;

    private Integer maxRetries;

    private LocalDateTime nextRetryAt;

    private String lastErrorMessage;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
