package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** `educloud.user.password` 配置：密码策略与 BCrypt 强度（设计规格第 5 节）。 */
@Validated
@ConfigurationProperties("educloud.user.password")
public record PasswordProperties(int minLength, int maxLength, int bcryptStrength) {
}
