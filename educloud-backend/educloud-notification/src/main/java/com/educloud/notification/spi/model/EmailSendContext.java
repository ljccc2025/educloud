package com.educloud.notification.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSendContext {
    private String to;
    private String subject;
    private String content;
    private boolean html;
    private Long notificationId;
    private Long userId;
}
