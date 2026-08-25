package com.educloud.payment.enums;

public enum PaymentChannel {
    MOCK,
    ALIPAY,
    WECHAT;

    public static PaymentChannel fromString(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Channel cannot be blank");
        }
        return PaymentChannel.valueOf(channel.trim().toUpperCase());
    }
}
