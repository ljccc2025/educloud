package com.educloud.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "educloud.payment")
public record PaymentProperties(
        String environment,
        JwtProperties jwt,
        InternalProperties internal,
        AlipayProperties alipay,
        WechatProperties wechat) {

    public record JwtProperties(
            String jwksLocation,
            String issuer,
            String audience) {
    }

    public record InternalProperties(
            String allowedClientIds,
            String audience,
            String secretToken) {

        public List<String> effectiveAllowedClientIds() {
            if (allowedClientIds == null || allowedClientIds.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedClientIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        public String effectiveInternalAudience() {
            return (audience != null && !audience.isBlank()) ? audience : "educloud-payment";
        }
    }

    public record AlipayProperties(
            String appId,
            String merchantPrivateKey,
            String alipayPublicKey,
            String notifyUrl,
            String gatewayHost) {
    }

    public record WechatProperties(
            String appId,
            String mchId,
            String apiV3Key,
            String merchantSerialNo,
            String privateKeyPath,
            String notifyUrl) {
    }
}
