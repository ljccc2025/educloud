package com.educloud.user.security;

import com.educloud.user.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserJwtEncoder 单元测试。依据：M03 设计规格第 4.2/8 节（Access Token 与服务 Token claims，
 * RS256 + kid，可用公钥验签）。
 */
class UserJwtEncoderTest {

    @TempDir
    Path tempDir;

    @Test
    void encodesAccessTokenClaimsVerifiableWithPublicKey() throws Exception {
        JwtKeyProvider provider = provider();
        UserJwtEncoder encoder = new UserJwtEncoder(provider);

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://issuer.educloud.local")
                .audience(List.of("educloud-api"))
                .subject("1960000000000000001")
                .claim("sid", "family-123")
                .claim("userType", "STUDENT")
                .claim("tokenVersion", 3L)
                .claim("roles", List.of("STUDENT"))
                .claim("permissions", List.of("course:read"))
                .issueTime(Date.from(now.minusSeconds(1)))
                .notBeforeTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .build();

        String token = encoder.encode(claims);
        SignedJWT signed = SignedJWT.parse(token);
        assertThat(signed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signed.getHeader().getKeyID()).startsWith("educloud-user-");

        JWSVerifier verifier = new RSASSAVerifier(
                ((com.nimbusds.jose.jwk.RSAKey) provider.publicJwkSet().getKeys().get(0))
                        .toRSAPublicKey());
        assertThat(signed.verify(verifier)).isTrue();

        JWTClaimsSet verified = signed.getJWTClaimsSet();
        assertThat(verified.getIssuer()).isEqualTo("https://issuer.educloud.local");
        assertThat(verified.getAudience()).contains("educloud-api");
        assertThat(verified.getSubject()).isEqualTo("1960000000000000001");
        assertThat(verified.getClaim("sid")).isEqualTo("family-123");
        assertThat(verified.getClaim("tokenVersion")).isEqualTo(3L);
        assertThat(verified.getClaim("userType")).isEqualTo("STUDENT");
    }

    @Test
    void encodesServiceTokenClaimsWithClientIdentity() throws Exception {
        JwtKeyProvider provider = provider();
        UserJwtEncoder encoder = new UserJwtEncoder(provider);

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://issuer.educloud.local")
                .audience(List.of("educloud-order"))
                .subject("service:order-service")
                .claim("clientId", "order-service")
                .claim("scope", List.of("course:read"))
                .jwtID("jti-1")
                .claim("tokenVersion", 1L)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();

        SignedJWT signed = SignedJWT.parse(encoder.encode(claims));
        assertThat(signed.getJWTClaimsSet().getSubject()).isEqualTo("service:order-service");
        assertThat(signed.getJWTClaimsSet().getClaim("clientId")).isEqualTo("order-service");
        assertThat(signed.getJWTClaimsSet().getJWTID()).isEqualTo("jti-1");
    }

    private JwtKeyProvider provider() throws Exception {
        Path keyFile = writePkcs8Key();
        return new JwtKeyProvider(new JwtProperties(
                keyFile.toString(), "https://issuer.educloud.local", "educloud-api",
                Duration.ofMinutes(5)));
    }

    private Path writePkcs8Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + wrap(base64) + "-----END PRIVATE KEY-----\n";
        Path keyFile = tempDir.resolve("encoder-private.pem");
        Files.write(keyFile, pem.getBytes(StandardCharsets.US_ASCII));
        return keyFile;
    }

    private static String wrap(String base64) {
        StringBuilder wrapped = new StringBuilder();
        for (int index = 0; index < base64.length(); index += 64) {
            wrapped.append(base64, index, Math.min(index + 64, base64.length())).append('\n');
        }
        return wrapped.toString();
    }
}
