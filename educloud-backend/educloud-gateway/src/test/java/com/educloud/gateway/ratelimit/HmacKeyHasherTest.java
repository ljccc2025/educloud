package com.educloud.gateway.ratelimit;

import com.educloud.gateway.config.GatewayRateLimitProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class HmacKeyHasherTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void computesStableDimensionSeparatedLowercaseSha256Digests() throws Exception {
        HmacKeyHasher hasher = hasher();

        String ordinary = hasher.digest("ordinary", "catalog\n198.51.100.25");

        Mac reference = Mac.getInstance("HmacSHA256");
        reference.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        String expected = HexFormat.of().formatHex(reference.doFinal(
                "ordinary\ncatalog\n198.51.100.25".getBytes(StandardCharsets.UTF_8)));
        assertThat(ordinary).isEqualTo(expected).matches("[0-9a-f]{64}");
        assertThat(hasher.digest("ordinary", "catalog\n198.51.100.25")).isEqualTo(ordinary);
        assertThat(hasher.digest("login-ip", "catalog\n198.51.100.25")).isNotEqualTo(ordinary);
    }

    @Test
    void neverExposesTheSecretFromToString() {
        HmacKeyHasher hasher = hasher();

        assertThat(hasher.toString())
                .doesNotContain(Base64.getEncoder().encodeToString(SECRET))
                .doesNotContain(new String(SECRET, StandardCharsets.UTF_8));
    }

    private static HmacKeyHasher hasher() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setHmacSecretBase64(Base64.getEncoder().encodeToString(SECRET));
        return new HmacKeyHasher(properties);
    }
}
