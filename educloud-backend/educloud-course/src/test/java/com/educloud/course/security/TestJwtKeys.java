package com.educloud.course.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测试用 RSA 密钥对：JWKS 只含公钥，私钥用于签发测试 JWT
 * （复制 gateway/file TestJwtKeys 模式，供 SecurityConfigTest 与 CourseContextTest 使用）。
 */
public final class TestJwtKeys {

    private final RSAKey signingKey;

    public TestJwtKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            signingKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID("test-" + UUID.randomUUID())
                    .build();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA test key generation is unavailable", exception);
        }
    }

    public String keyId() {
        return signingKey.getKeyID();
    }

    public String publicJwksJson() {
        return new JWKSet(signingKey.toPublicJWK()).toString();
    }

    public static String publicJwksJson(TestJwtKeys... keys) {
        return new JWKSet(List.of(keys).stream()
                .map(key -> (JWK) key.signingKey.toPublicJWK())
                .toList()).toString();
    }

    public String signedToken(Map<String, Object> claims) {
        return signedToken(claims, JWSAlgorithm.RS256, keyId());
    }

    public String signedToken(Map<String, Object> claims, JWSAlgorithm algorithm, String keyId) {
        try {
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(algorithm).keyID(keyId).build(),
                    claims(claims));
            token.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
            return token.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("temporary JWT signing failed", exception);
        }
    }

    String unsignedToken(Map<String, Object> claims) {
        return new PlainJWT(claims(claims)).serialize();
    }

    private static JWTClaimsSet claims(Map<String, Object> claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        claims.forEach((name, value) -> setClaim(builder, name, value));
        return builder.build();
    }

    private static void setClaim(JWTClaimsSet.Builder builder, String name, Object value) {
        if ("iss".equals(name) && value instanceof String issuer) {
            builder.issuer(issuer);
        } else if ("sub".equals(name) && value instanceof String subject) {
            builder.subject(subject);
        } else if ("aud".equals(name) && value instanceof Collection<?> audience) {
            builder.audience(audience.stream().map(String::valueOf).toList());
        } else if ("exp".equals(name) && value instanceof Instant expiration) {
            builder.expirationTime(Date.from(expiration));
        } else if ("nbf".equals(name) && value instanceof Instant notBefore) {
            builder.notBeforeTime(Date.from(notBefore));
        } else if ("iat".equals(name) && value instanceof Instant issuedAt) {
            builder.issueTime(Date.from(issuedAt));
        } else {
            builder.claim(name, value);
        }
    }
}