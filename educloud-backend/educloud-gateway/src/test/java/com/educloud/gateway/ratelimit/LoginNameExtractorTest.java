package com.educloud.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginNameExtractorTest {

    private final LoginNameExtractor extractor = new LoginNameExtractor(new ObjectMapper());

    @Test
    void normalizesTheRootStringLoginNameWithNfkcTrimAndRootLowercase() {
        assertThat(extractor.extract(bytes("{\"loginName\":\"  ＡLICE  \"}")))
                .isEqualTo("alice");
    }

    @Test
    void rejectsMalformedAmbiguousOrMissingLoginNamesWithASafeMessage() {
        for (String json : List.of(
                "not-json",
                "[]",
                "{}",
                "{\"loginName\":1}",
                "{\"loginName\":null}",
                "{\"loginName\":\"\"}",
                "{\"loginName\":\"" + "x".repeat(129) + "\"}",
                "{\"loginName\":\"first\",\"loginName\":\"second\",\"password\":\"secret\"}")) {
            assertThatThrownBy(() -> extractor.extract(bytes(json)))
                    .isInstanceOf(LoginNameExtractor.LoginNameExtractionException.class)
                    .hasMessage("loginName could not be parsed safely")
                    .hasMessageNotContaining("password")
                    .hasMessageNotContaining("secret")
                    .hasMessageNotContaining(json);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
