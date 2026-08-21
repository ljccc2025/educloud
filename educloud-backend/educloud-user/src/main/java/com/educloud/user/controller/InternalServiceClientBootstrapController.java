package com.educloud.user.controller;

import com.educloud.user.command.ServiceClientCredentialCommand;
import com.educloud.user.config.InternalProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
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

    private final ServiceClientCredentialCommand command;
    private final InternalProperties internalProperties;

    public InternalServiceClientBootstrapController(
            ServiceClientCredentialCommand command, InternalProperties internalProperties) {
        this.command = command;
        this.internalProperties = internalProperties;
    }

    @PostMapping(value = "/service-clients", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> bootstrap(
            @RequestHeader(BOOTSTRAP_KEY_HEADER) String bootstrapKey,
            @RequestBody BootstrapRequest request,
            HttpServletRequest servletRequest) {
        if (internalProperties.bootstrapKey() == null
                || !internalProperties.bootstrapKey().equals(bootstrapKey)) {
            throw new org.springframework.security.access.AccessDeniedException("invalid bootstrap key");
        }
        command.bootstrap(
                request.clientId(),
                request.secret(),
                request.allowedAudiences(),
                request.allowedScopes(),
                servletRequest.getHeader("X-Request-Id"));
        return Map.of("clientId", request.clientId(), "status", "BOOTSTRAPPED");
    }

    public record BootstrapRequest(
            String clientId,
            String secret,
            List<String> allowedAudiences,
            List<String> allowedScopes) {
    }
}
