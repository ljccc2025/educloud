package com.educloud.user.security;

import com.educloud.user.config.JwtProperties;
import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtKeyProvider 单元测试。依据：M03 计划任务 5（密钥文件缺失/格式错误启动失败；
 * JWKS 只含公钥；kid 派生稳定）。
 */
class JwtKeyProviderTest {

    @TempDir
    Path tempDir;

    private static final String KID_PREFIX = "educloud-user-";

    @Test
    void loadsPkcs8RsaKeyAndExposesPublicOnlyJwks() throws Exception {
        Path keyFile = writePkcs8Key();

        JwtKeyProvider provider = new JwtKeyProvider(
                new JwtProperties(keyFile.toString(), "https://issuer.educloud.local",
                        "educloud-api", java.time.Duration.ofMinutes(5)));

        assertThat(provider.keyId()).startsWith(KID_PREFIX);
        assertThat(provider.signingKey().isPrivate()).isTrue();
        assertThat(provider.publicJwkSet().getKeys()).hasSize(1);
        JWK publicKey = provider.publicJwkSet().getKeys().get(0);
        assertThat(publicKey.isPrivate()).isFalse();
        assertThat(publicKey.toJSONObject().keySet())
                .doesNotContain("d", "p", "q", "dp", "dq", "qi", "oth");
        assertThat(publicKey.getKeyID()).isEqualTo(provider.keyId());
    }

    @Test
    void sameKeyProducesStableKid() throws Exception {
        Path keyFile = writePkcs8Key();
        JwtKeyProvider first = new JwtKeyProvider(new JwtProperties(
                keyFile.toString(), "https://issuer.educloud.local", "educloud-api",
                java.time.Duration.ofMinutes(5)));
        JwtKeyProvider second = new JwtKeyProvider(new JwtProperties(
                keyFile.toString(), "https://issuer.educloud.local", "educloud-api",
                java.time.Duration.ofMinutes(5)));
        assertThat(second.keyId()).isEqualTo(first.keyId());
    }

    @Test
    void rejectsMissingFileAndInvalidKeyMaterial() throws Exception {
        JwtProperties missing = new JwtProperties(
                tempDir.resolve("missing.pem").toString(), "issuer", "aud",
                java.time.Duration.ofMinutes(5));
        assertThatThrownBy(() -> new JwtKeyProvider(missing))
                .isInstanceOf(IllegalStateException.class);

        Path notPem = tempDir.resolve("not-a-key.pem");
        Files.writeString(notPem, "not a key");
        JwtProperties invalid = new JwtProperties(
                notPem.toString(), "issuer", "aud", java.time.Duration.ofMinutes(5));
        assertThatThrownBy(() -> new JwtKeyProvider(invalid))
                .isInstanceOf(IllegalStateException.class);
    }

    private Path writePkcs8Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + wrap(base64)
                + "-----END PRIVATE KEY-----\n";
        Path keyFile = tempDir.resolve("test-private.pem");
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
