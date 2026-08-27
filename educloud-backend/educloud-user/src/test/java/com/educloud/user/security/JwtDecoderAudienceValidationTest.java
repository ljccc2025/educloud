package com.educloud.user.security;

import com.educloud.user.config.JwtProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityConfiguration 解码器接线测试（BUG-038，真实 RSA 密钥 + Nimbus 解码器，无 Spring 上下文）：
 * 1. resource-server 解码器（jwtDecoder）组合 iss/exp + aud 校验：aud 匹配通过，aud 不匹配/缺失拒绝
 *    （解码失败在 resource-server 链路即表现为 401）；
 * 2. internal 解码器（internalJwtDecoder）不做用户令牌 aud 校验：服务令牌（aud=educloud-user）
 *    仍可解码，InternalApiFilter 内部服务令牌链路不受影响。
 */
class JwtDecoderAudienceValidationTest {

    private static final String ISSUER = "https://issuer.educloud.local";
    private static final String USER_AUDIENCE = "educloud-api";

    @TempDir
    Path tempDir;

    private JwtKeyProvider keyProvider;
    private UserJwtEncoder encoder;
    private JwtDecoder resourceServerDecoder;
    private JwtDecoder internalDecoder;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties properties = new JwtProperties(
                writePkcs8Key().toString(), ISSUER, USER_AUDIENCE, Duration.ofMinutes(5));
        keyProvider = new JwtKeyProvider(properties);
        encoder = new UserJwtEncoder(keyProvider);
        SecurityConfiguration configuration = new SecurityConfiguration();
        resourceServerDecoder = configuration.jwtDecoder(keyProvider, properties);
        internalDecoder = configuration.internalJwtDecoder(keyProvider, properties);
    }

    @Test
    @DisplayName("aud 匹配的用户令牌可通过 resource-server 解码器")
    void userTokenWithMatchingAudienceDecodes() {
        String token = sign(claims(List.of(USER_AUDIENCE), null));
        Jwt decoded = resourceServerDecoder.decode(token);
        assertThat(decoded.getSubject()).isEqualTo("1960000000000000001");
        assertThat(decoded.getAudience()).contains(USER_AUDIENCE);
    }

    @Test
    @DisplayName("aud 不匹配的用户令牌被 resource-server 解码器拒绝（对应 401）")
    void userTokenWithWrongAudienceIsRejected() {
        String token = sign(claims(List.of("educloud-user"), null));
        assertThatThrownBy(() -> resourceServerDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("audience");
    }

    @Test
    @DisplayName("aud 缺失的用户令牌被 resource-server 解码器拒绝")
    void userTokenWithoutAudienceIsRejected() {
        String token = sign(claims(null, null));
        assertThatThrownBy(() -> resourceServerDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("audience");
    }

    @Test
    @DisplayName("internal 服务令牌不受影响：internal 解码器可解码，resource-server 解码器拒绝")
    void internalServiceTokenStillDecodesWithInternalDecoder() {
        String token = sign(claims(List.of("educloud-user"), "order-service"));

        Jwt decoded = internalDecoder.decode(token);
        assertThat(decoded.getClaimAsString("clientId")).isEqualTo("order-service");
        assertThat(decoded.getAudience()).contains("educloud-user");

        // 服务令牌不可复用为 resource-server 用户令牌（aud 误用面收敛）
        assertThatThrownBy(() -> resourceServerDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("audience");
    }

    private JWTClaimsSet claims(List<String> audience, String clientId) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("1960000000000000001")
                .issueTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plusSeconds(300)));
        if (audience != null) {
            builder.audience(audience);
        }
        if (clientId != null) {
            builder.claim("clientId", clientId);
        }
        return builder.build();
    }

    private String sign(JWTClaimsSet claims) {
        return encoder.encode(claims);
    }

    private Path writePkcs8Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + wrap(base64) + "-----END PRIVATE KEY-----\n";
        Path keyFile = tempDir.resolve("decoder-audience-test.pem");
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
