package com.educloud.notification.dto.response;

import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.enums.TargetType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id; // user_notification.id

    @JsonSerialize(using = ToStringSerializer.class)
    private Long notificationId;

    private String title;

    private String content;

    private NotificationKind kind;

    private TargetType targetType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    private String actionLabel;

    private String actionPath;

    private boolean read;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
