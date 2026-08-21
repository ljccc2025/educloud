package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * `educloud.user.session` 配置。依据：M03 设计规格第 4 节（与 Gateway 会话契约对齐）。
 * environment 必须与 Gateway 的 educloud.gateway.environment 同源（同一 EDUCLOUD_ENVIRONMENT）。
 */
@Validated
@ConfigurationProperties("educloud.user.session")
public record SessionProperties(
        String environment,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration rotationGraceWindow,
        boolean cookieSecure) {
}
