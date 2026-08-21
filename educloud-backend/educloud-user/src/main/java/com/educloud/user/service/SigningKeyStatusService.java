package com.educloud.user.service;

import com.educloud.user.dto.response.SigningKeyStatusResponse;
import com.educloud.user.security.JwtKeyProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * JWT 签名公钥非敏感状态。依据：API 规范第 7 节（security:key-status:read；
 * 只返回活动 kid、公钥数量、更新时间；不含私钥）。
 */
@Service
public final class SigningKeyStatusService {

    private final JwtKeyProvider keyProvider;

    public SigningKeyStatusService(JwtKeyProvider keyProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
    }

    public SigningKeyStatusResponse status() {
        // nextRotationAt：生产轮换流程落地前为 null（TODO：与 Secret 轮换计划联动）。
        return new SigningKeyStatusResponse(
                keyProvider.keyId(),
                keyProvider.publicJwkSet().getKeys().size(),
                Instant.now(),
                null);
    }
}
