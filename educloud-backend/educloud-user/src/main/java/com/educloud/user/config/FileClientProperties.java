package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * `educloud.user.file` 配置：File 服务内部地址与服务令牌凭据（M04 任务 14）。
 * enabled=false 时 FileClient 全部 no-op，保证本地无 File 服务时 User 可用。
 */
@Validated
@ConfigurationProperties("educloud.user.file")
public record FileClientProperties(
        String endpoint,
        String clientId,
        String clientSecret,
        Boolean enabled,
        Duration timeout) {

    public FileClientProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://127.0.0.1:8087";
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "user-service";
        }
        if (enabled == null) {
            enabled = true;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(3);
        }
    }
}
