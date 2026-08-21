package com.educloud.gateway.ratelimit;

import com.educloud.gateway.config.GatewayRateLimitProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Component
public final class HmacKeyHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public HmacKeyHasher(GatewayRateLimitProperties properties) {
        Objects.requireNonNull(properties, "properties");
        try {
            this.secret = Base64.getDecoder().decode(properties.getHmacSecretBase64());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("rate-limit HMAC secret is invalid");
        }
        if (secret.length < 32) {
            throw new IllegalArgumentException("rate-limit HMAC secret must be at least 32 bytes");
        }
    }

    public String digest(String dimension, String normalizedValue) {
        if (dimension == null || !dimension.matches("[a-z0-9-]{1,32}")) {
            throw new IllegalArgumentException("invalid HMAC dimension");
        }
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            throw new IllegalArgumentException("normalized HMAC value must not be empty");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] input = (dimension + "\n" + normalizedValue).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(input));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable");
        }
    }

    @Override
    public String toString() {
        return "HmacKeyHasher[algorithm=HmacSHA256]";
    }
}
