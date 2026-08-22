package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * `educloud.user.internal` 配置：内部服务接口的 bootstrap 密钥、允许的 clientId 白名单
 * 与内部接口期望的 audience（安全设计第 8 节：接口 ACL；凭据不进 URL/日志）。
 */
@Validated
@ConfigurationProperties("educloud.user.internal")
public record InternalProperties(
        String bootstrapKey,
        List<String> allowedClientIds,
        String internalAudience) {

    /** 内部接口期望的 audience；未配置时默认 educloud-user。 */
    public String effectiveInternalAudience() {
        return internalAudience == null || internalAudience.isBlank()
                ? "educloud-user"
                : internalAudience;
    }
}
