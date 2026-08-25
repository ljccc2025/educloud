package com.educloud.notification.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSendResult {
    private boolean success;
    private String messageId;
    private String errorMessage;

    public static EmailSendResult success(String messageId) {
        return EmailSendResult.builder()
                .success(true)
                .messageId(messageId)
                .build();
    }

    public static EmailSendResult failed(String errorMessage) {
        return EmailSendResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
