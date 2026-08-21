package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewaySecurityProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwksLoaderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwksLoader loader = new JwksLoader();

    @Test
    void requiresExactlyOneJwksSource() {
        GatewaySecurityProperties missing = properties(null);
        assertRejected(missing, "exactly one");

        GatewaySecurityProperties both = properties(new TestJwtKeys().publicJwksJson());
        both.setJwksLocation(new ByteArrayResource(new byte[0]));
        assertRejected(both, "exactly one");
    }

    @Test
    void rejectsMalformedEmptyAndUnusableKeySets() throws Exception {
        assertRejected(properties("not-json"), "invalid JWKS");
        assertRejected(properties("{\"keys\":[]}"), "at least one");

        OctetSequenceKey nonRsa = new OctetSequenceKey.Builder(new byte[32])
                .keyID("oct-test")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        assertRejected(properties(new JWKSet(nonRsa).toString(false)), "RSA");

        TestJwtKeys keys = new TestJwtKeys();
        ObjectNode invalidModulus = root(keys);
        firstKey(invalidModulus).put("n", "not-base64url!");
        assertRejected(properties(invalidModulus.toString()), "invalid JWKS");
    }

    @Test
    void rejectsWrongUseAlgorithmMissingAndDuplicateKeyIds() throws Exception {
        TestJwtKeys keys = new TestJwtKeys();

        ObjectNode wrongUse = root(keys);
        firstKey(wrongUse).put("use", "enc");
        assertRejected(properties(wrongUse.toString()), "use=sig");

        ObjectNode wrongAlgorithm = root(keys);
        firstKey(wrongAlgorithm).put("alg", "RS512");
        assertRejected(properties(wrongAlgorithm.toString()), "alg=RS256");

        ObjectNode missingKeyId = root(keys);
        firstKey(missingKeyId).remove("kid");
        assertRejected(properties(missingKeyId.toString()), "kid");

        ObjectNode blankKeyId = root(keys);
        firstKey(blankKeyId).put("kid", " ");
        assertRejected(properties(blankKeyId.toString()), "kid");

        ObjectNode duplicate = root(keys);
        ArrayNode array = (ArrayNode) duplicate.get("keys");
        array.add(array.get(0).deepCopy());
        assertRejected(properties(duplicate.toString()), "duplicate kid");
    }

    @Test
    void rejectsEveryPrivateRsaParameterWithoutEchoingInput() throws Exception {
        TestJwtKeys keys = new TestJwtKeys();
        for (String privateParameter : List.of("d", "p", "q", "dp", "dq", "qi", "oth")) {
            ObjectNode root = root(keys);
            firstKey(root).put(privateParameter, "synthetic-test-value");
            String source = root.toString();
            assertThatThrownBy(() -> loader.load(properties(source)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("private")
                    .hasMessageNotContaining("synthetic-test-value");
        }
    }

    @Test
    void loadsOnlyImmutablePublicKeysAndSupportsARegularFile(@TempDir Path tempDir) throws Exception {
        TestJwtKeys first = new TestJwtKeys();
        TestJwtKeys second = new TestJwtKeys();
        String json = TestJwtKeys.publicJwksJson(first, second);

        JwksLoader.LoadedJwks inline = loader.load(properties(json));
        assertThat(inline.keyIds()).containsExactlyInAnyOrder(first.keyId(), second.keyId());
        assertThat(inline.jwkSet().getKeys()).allMatch(key -> !key.isPrivate());
        assertThatThrownBy(() -> inline.keyIds().add("another")).isInstanceOf(UnsupportedOperationException.class);

        Path jwksFile = tempDir.resolve("gateway-jwks.json");
        Files.writeString(jwksFile, json, StandardCharsets.UTF_8);
        GatewaySecurityProperties fileProperties = properties(null);
        fileProperties.setJwksLocation(new FileSystemResource(jwksFile));
        assertThat(loader.load(fileProperties).keyIds()).containsExactlyInAnyOrder(first.keyId(), second.keyId());
    }

    @Test
    void rejectsNonFileAndOversizedResources(@TempDir Path tempDir) throws Exception {
        GatewaySecurityProperties nonFile = properties(null);
        nonFile.setJwksLocation(new ByteArrayResource(new TestJwtKeys().publicJwksJson()
                .getBytes(StandardCharsets.UTF_8)));
        assertRejected(nonFile, "regular readable file");

        Path oversized = tempDir.resolve("oversized.json");
        Files.write(oversized, new byte[256 * 1024 + 1]);
        GatewaySecurityProperties oversizedProperties = properties(null);
        oversizedProperties.setJwksLocation(new FileSystemResource(oversized));
        assertRejected(oversizedProperties, "256 KiB");
    }

    private static GatewaySecurityProperties properties(String json) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setJwksJson(json);
        properties.setIssuer("https://identity.educloud.local");
        return properties;
    }

    private static ObjectNode root(TestJwtKeys keys) throws Exception {
        return (ObjectNode) OBJECT_MAPPER.readTree(keys.publicJwksJson());
    }

    private static ObjectNode firstKey(ObjectNode root) {
        JsonNode key = root.withArray("keys").get(0);
        return (ObjectNode) key;
    }

    private void assertRejected(GatewaySecurityProperties properties, String category) {
        assertThatThrownBy(() -> loader.load(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(category);
    }
}
