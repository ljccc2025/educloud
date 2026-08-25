package com.educloud.notification.exception;

import com.educloud.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", "通知不存在或已被删除", 404),
    INVALID_TARGET_USER("INVALID_TARGET_USER", "指定的接收人无效", 400),
    EMAIL_TEST_RATE_LIMITED("EMAIL_TEST_RATE_LIMITED", "测试邮件发送过于频繁，请稍后再试", 429),
    EMAIL_CHANNEL_DISABLED("EMAIL_CHANNEL_DISABLED", "邮件通知渠道已被禁用", 400),
    EMAIL_SEND_FAILED("EMAIL_SEND_FAILED", "邮件投递失败", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
