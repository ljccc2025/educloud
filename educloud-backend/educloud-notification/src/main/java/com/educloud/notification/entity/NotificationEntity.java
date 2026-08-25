package com.educloud.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.enums.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_notification")
public class NotificationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String content;

    private NotificationKind kind;

    private TargetType targetType;

    private Long senderId;

    private String actionLabel;

    private String actionPath;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
