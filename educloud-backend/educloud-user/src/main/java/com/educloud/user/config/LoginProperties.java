package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** `educloud.user.login` 配置：失败锁定阈值与时长（设计规格第 5 节）。 */
@Validated
@ConfigurationProperties("educloud.user.login")
public record LoginProperties(int maxFailedAttempts, Duration lockDuration) {
}
