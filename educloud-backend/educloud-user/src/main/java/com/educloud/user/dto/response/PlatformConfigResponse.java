package com.educloud.user.dto.response;

/** 平台公开配置项。 */
public record PlatformConfigResponse(
        String configKey,
        String configValue,
        String valueType,
        String description,
        Integer version) {
}
