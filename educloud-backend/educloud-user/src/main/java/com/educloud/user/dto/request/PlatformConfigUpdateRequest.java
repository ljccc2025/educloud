package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 平台公开配置更新请求（仅非敏感配置；Secret 不提供产品侧更新 API，安全设计第 9 节）。 */
public record PlatformConfigUpdateRequest(
        @NotBlank
        @Size(max = 64)
        String configKey,

        @NotBlank
        @Size(max = 1024)
        String configValue,

        @NotBlank
        @Size(max = 16)
        String valueType,

        @Size(max = 255)
        String description) {
}
