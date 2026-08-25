package com.educloud.notification.dto.request;

import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.enums.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishNotificationRequest {

    @NotBlank(message = "通知标题不能为空")
    private String title;

    @NotBlank(message = "通知正文不能为空")
    private String content;

    @NotNull(message = "通知分类不能为空")
    private NotificationKind kind;

    @NotNull(message = "受众类型不能为空")
    private TargetType targetType;

    private List<Long> targetUserIds;

    private String targetRole;

    private String actionLabel;

    private String actionPath;

    private boolean sendEmail;
}
