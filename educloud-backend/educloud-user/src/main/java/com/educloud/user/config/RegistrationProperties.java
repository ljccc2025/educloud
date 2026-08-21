package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** `educloud.user.registration` 配置：学生自助注册开关（设计规格第 5 节）。 */
@Validated
@ConfigurationProperties("educloud.user.registration")
public record RegistrationProperties(boolean enabled) {
}
