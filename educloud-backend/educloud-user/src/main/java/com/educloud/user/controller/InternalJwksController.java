package com.educloud.user.controller;

import com.educloud.user.security.InternalApiFilter;
import com.educloud.user.security.JwtKeyProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公共 JWKS 出口（仅公钥；Gateway 配置引用静态 jwks 文件，本端点供部署校验）。
 * 依据：M03 设计规格第 10.2 节与 M02 JwksLoader 契约（无私钥参数）。
 */
@RestController
@RequestMapping("/internal/v1")
public final class InternalJwksController {

    private final JwtKeyProvider keyProvider;

    public InternalJwksController(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @GetMapping(value = "/public-jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> publicJwks(HttpServletRequest request) {
        InternalApiFilter.requireClientId(request);
        return keyProvider.publicJwkSet().toJSONObject();
    }
}
