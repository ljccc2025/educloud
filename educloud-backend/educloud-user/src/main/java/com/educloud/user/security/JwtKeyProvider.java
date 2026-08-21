package com.educloud.user.security;

import com.educloud.user.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 加载 User 服务 RSA 私钥并派生公共 JWKS。
 *
 * <p>依据：M03 设计规格第 2/5 节（签名密钥管理）：私钥只在 User 服务，Gateway 持公钥；
 * JWKS 输出只含公钥参数；kid 由公钥模数哈希派生（与 generate-user-jwt-keys.sh 一致），
 * 密钥对不变则 kid 稳定，支持平滑轮换。</p>
 */
@Component
public final class JwtKeyProvider {

    private static final long MAX_KEY_BYTES = 64 * 1024;
    private static final String KID_PREFIX = "educloud-user-";

    private final RSAKey signingKey;
    private final JWKSet publicJwkSet;
    private final JWKSource<SecurityContext> jwkSource;
    private final String keyId;

    public JwtKeyProvider(JwtProperties properties) {
        Objects.requireNonNull(properties, "properties");
        String location = properties.privateKeyLocation();
        if (location == null || location.isBlank()) {
            throw new IllegalStateException(
                    "educloud.user.jwt.private-key-location must be configured (see M03 design section 11)");
        }
        RSAPrivateCrtKey privateKey = readPrivateKey(location);
        this.keyId = keyId(privateKey);
        // Nimbus RSAKey.Builder 只有公钥/RSAKey 构造器：由 CRT 私钥推导公钥后再组装私钥 JWK。
        this.signingKey = new RSAKey.Builder(publicKey(privateKey))
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        this.publicJwkSet = new JWKSet(java.util.List.of(signingKey.toPublicJWK()));
        this.jwkSource = new ImmutableJWKSet<>(publicJwkSet);
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    public JWKSet publicJwkSet() {
        return publicJwkSet;
    }

    public JWKSource<SecurityContext> jwkSource() {
        return jwkSource;
    }

    public String keyId() {
        return keyId;
    }

    private static java.security.interfaces.RSAPublicKey publicKey(RSAPrivateCrtKey privateKey) {
        try {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    privateKey.getModulus(), privateKey.getPublicExponent());
            return (java.security.interfaces.RSAPublicKey)
                    KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to derive RSA public key", exception);
        }
    }

    private static RSAPrivateCrtKey readPrivateKey(String location) {
        Path path = Path.of(location);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(
                    "JWT private key must be a regular readable file: " + location);
        }
        try {
            if (Files.size(path) > MAX_KEY_BYTES) {
                throw new IllegalStateException("JWT private key file exceeds 64 KiB: " + location);
            }
            String pem = Files.readString(path, StandardCharsets.US_ASCII);
            String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (IOException exception) {
            throw new IllegalStateException("JWT private key cannot be read: " + location, exception);
        } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "JWT private key must be an unencrypted PKCS#8 RSA key: " + location, exception);
        }
    }

    private static String keyId(RSAPrivateKey privateKey) {
        byte[] modulus = privateKey.getModulus().toByteArray();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(modulus);
            return KID_PREFIX + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
