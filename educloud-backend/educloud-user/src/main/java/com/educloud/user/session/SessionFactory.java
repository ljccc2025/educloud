package com.educloud.user.session;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 创建 Refresh 会话族：family_id 即 Access Token 的 sid；Refresh Token 为 256-bit 随机值，
 * 只保存 SHA-256 哈希（安全设计第 3.2 节、数据设计第 3 节）。
 */
@Component
public final class SessionFactory {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionCreated create(Long userId, String clientType, String clientFingerprintHash) {
        String familyId = UUID.randomUUID().toString();
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        return new SessionCreated(
                familyId,
                rawToken,
                sha256Hex(rawToken),
                clientType,
                clientFingerprintHash,
                Instant.now());
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SessionCreated(
            String familyId,
            String rawToken,
            String tokenHash,
            String clientType,
            String clientFingerprintHash,
            Instant issuedAt) {
    }
}
