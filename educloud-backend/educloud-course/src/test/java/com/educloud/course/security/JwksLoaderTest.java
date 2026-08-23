package com.educloud.course.security;

import com.educloud.course.config.CourseProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JwksLoader 边界测试：file:/classpath: 加载、空文件、空 key 集、重复 kid、私钥参数、超限、缺省配置。 */
class JwksLoaderTest {

    @TempDir
    Path tempDir;

    private final JwksLoader loader = new JwksLoader();

    @Test
    void loadsPublicJwksFromFileLocation() throws Exception {
        TestJwtKeys keys = new TestJwtKeys();
        Path file = tempDir.resolve("jwks.json");
        Files.writeString(file, keys.publicJwksJson());

        JwksLoader.LoadedJwks loaded = loader.load(properties("file:" + file.toAbsolutePath()));

        assertThat(loaded.keyIds()).containsExactly(keys.keyId());
        assertThat(loaded.jwkSet().getKeys()).hasSize(1);
    }

    @Test
    void loadsPublicJwksFromClasspathPrefix() {
        JwksLoader.LoadedJwks loaded = loader.load(properties("classpath:jwks-loader-fixture.json"));

        assertThat(loaded.keyIds()).containsExactly("educloud-course-loader-test-1");
    }

    @Test
    void rejectsMissingLocation() {
        assertThatThrownBy(() -> loader.load(properties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured");
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        Path file = tempDir.resolve("empty.json");
        Files.writeString(file, "");

        assertThatThrownBy(() -> loader.load(properties("file:" + file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid JWKS syntax");
    }

    @Test
    void rejectsEmptyKeySet() throws Exception {
        Path file = tempDir.resolve("nokeys.json");
        Files.writeString(file, "{\"keys\":[]}");

        assertThatThrownBy(() -> loader.load(properties("file:" + file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one public key");
    }

    @Test
    void rejectsDuplicateKid() throws Exception {
        TestJwtKeys first = new TestJwtKeys();
        TestJwtKeys second = new TestJwtKeys();
        Path file = tempDir.resolve("duplicate.json");
        Files.writeString(file, TestJwtKeys.publicJwksJson(first, second)
                .replace(second.keyId(), first.keyId()));

        assertThatThrownBy(() -> loader.load(properties("file:" + file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate kid");
    }

    @Test
    void rejectsPrivateKeyParameters() throws Exception {
        // 手写含私钥参数的 JWKS JSON：rejectPrivateParameters 在 JWKSet.parse 之前
        // 按原始 JSON 检查（nimbus JWKSet.toString() 不会序列化私钥参数，故不能依赖它构造）。
        Path file = tempDir.resolve("private.json");
        Files.writeString(file, "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"private-test-1\","
                + "\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"AQAB\",\"e\":\"AQAB\",\"d\":\"FAKE\"}]}");

        assertThatThrownBy(() -> loader.load(properties("file:" + file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not contain private key parameters");
    }

    @Test
    void rejectsOversizedJwks() throws Exception {
        Path file = tempDir.resolve("oversize.json");
        Files.writeString(file, "{ \"keys\": [] }".replace(" ", " ".repeat(300 * 1024)));

        assertThatThrownBy(() -> loader.load(properties("file:" + file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds 256 KiB");
    }

    private static CourseProperties properties(String location) {
        return new CourseProperties(
                "test",
                new CourseProperties.Jwt(location, "https://issuer.educloud.local", "educloud-api"),
                new CourseProperties.Internal(List.of("educloud-content"), "educloud-course"));
    }
}
