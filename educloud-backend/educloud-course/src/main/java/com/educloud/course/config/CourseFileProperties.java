package com.educloud.course.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * `educloud.course.file` 配置：File 服务内部地址、服务令牌凭据与超时（M05 任务 12）。
 *
 * <p>依据：M05 设计规格第 10 节 —— FileClient 默认 endpoint http://127.0.0.1:8087、
 * clientId educloud-course、enabled=true；任务 0 质量审查提示补 timeout 默认值（3s）。
 * enabled=false 时 FileClient 全部 no-op，保证本地无 File 服务时 Course 可用。
 * tokenEndpoint 为 User 服务令牌端点（M03 规格 §8 POST /internal/v1/service-tokens），
 * Course 以 HTTP Basic client_credentials 换取 aud=educloud-file 的服务令牌。</p>
 */
@Validated
@ConfigurationProperties("educloud.course.file")
public record CourseFileProperties(
        String endpoint,
        String clientId,
        String clientSecret,
        Boolean enabled,
        Duration timeout,
        String tokenEndpoint) {

    public CourseFileProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://127.0.0.1:8087";
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "educloud-course";
        }
        if (enabled == null) {
            enabled = true;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(3);
        }
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            tokenEndpoint = "http://127.0.0.1:8082";
        }
    }
}
