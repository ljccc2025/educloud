package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * `educloud.user.jwt` 配置。依据：M03 设计规格第 11 节。
 * issuer/audience 必须与 Gateway 的 GATEWAY_JWT_ISSUER/GATEWAY_JWT_AUDIENCE 一致。
 */
@Validated
@ConfigurationProperties("educloud.user.jwt")
public record JwtProperties(
        String privateKeyLocation,
        String issuer,
        String audience,
        Duration serviceTokenTtl) {
}
