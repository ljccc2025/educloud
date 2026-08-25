package com.educloud.user.controller;

import com.educloud.user.command.ServiceClientCredentialCommand;
import com.educloud.user.config.InternalProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务客户端 bootstrap（非产品 API；SERVICE_BOOTSTRAP_JOB 主体，X-Bootstrap-Key 保护，
 * Secret 从 stdin/Secret 文件进入，不进 URL/argv/日志；安全设计第 8 节）。
 * 路径刻意不在 /internal/v1/** 下，避免被服务 Token 过滤器拦截。
 */
@RestController
@RequestMapping("/internal/bootstrap")
public final class InternalServiceClientBootstrapController {

    private static final String BOOTSTRAP_KEY_HEADER = "X-Bootstrap-Key";
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalServiceClientBootstrapController.class);

    private final ServiceClientCredentialCommand command;
    private final InternalProperties internalProperties;

    public InternalServiceClientBootstrapController(
            ServiceClientCredentialCommand command, InternalProperties internalProperties) {
        this.command = command;
        this.internalProperties = internalProperties;
        // BUG-036 修复：默认占位绑定空串而非 null，启动时提示运维；
        // 请求侧已 fail-closed（空白密钥一律拒绝），未配置时端点不可用。
        String key = internalProperties.bootstrapKey();
        if (key == null || key.isBlank()) {
            LOGGER.warn("educloud.user.internal.bootstrap-key is blank; "
                    + "/internal/bootstrap/** endpoints are disabled (fail-closed)");
        }
    }

    @PostMapping(value = "/service-clients", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> bootstrap(
            @RequestHeader(BOOTSTRAP_KEY_HEADER) String bootstrapKey,
            @RequestBody BootstrapRequest request,
            HttpServletRequest servletRequest) {
        // BUG-036 修复：默认占位 `${EDUCLOUD_USER_INTERNAL_BOOTSTRAP_KEY:}` 绑定空串，
        // 只判 null 时 MessageDigest.isEqual("".bytes, "".bytes) 恒真，空 key 头
        // 即可绕过鉴权；空白密钥配置一律 fail-closed 拒绝。
        String configuredKey = internalProperties.bootstrapKey();
        if (configuredKey == null || configuredKey.isBlank()
                || !MessageDigest.isEqual(
                        configuredKey.getBytes(StandardCharsets.UTF_8),
                        bootstrapKey.getBytes(StandardCharsets.UTF_8))) {
            throw new org.springframework.security.access.AccessDeniedException("invalid bootstrap key");
        }
        command.bootstrap(
                request.clientId(),
                request.secret(),
                request.allowedAudiences(),
                request.allowedScopes(),
                com.educloud.user.support.RequestIds.from(servletRequest));
        return Map.of("clientId", request.clientId(), "status", "BOOTSTRAPPED");
    }

    public record BootstrapRequest(
            String clientId,
            String secret,
            List<String> allowedAudiences,
            List<String> allowedScopes) {
    }
}
