package com.educloud.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SessionScriptContractTest {

    private static final String RESOURCE = "com/educloud/gateway/security/check-session.lua";

    @Test
    void readsOnlyTheThreeSessionFieldsAndTtl() throws Exception {
        String script = new ClassPathResource(RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        String compact = script.replaceAll("\\s+", " ").trim();

        assertThat(compact).contains(
                "redis.call('HMGET', KEYS[1], 'subject', 'status', 'tokenVersion')",
                "redis.call('PTTL', KEYS[1])",
                "if ttl == -2 then",
                "return {0}",
                "return {1, values[1] or '', values[2] or '', values[3] or '', ttl}");
    }

    @Test
    void containsNoMutationOrUnboundedKeyCommands() throws Exception {
        String script = new ClassPathResource(RESOURCE)
                .getContentAsString(StandardCharsets.UTF_8)
                .toUpperCase(Locale.ROOT);

        assertThat(command(script, "HMGET")).isTrue();
        assertThat(command(script, "PTTL")).isTrue();
        assertThat(command(script, "HSET")).isFalse();
        assertThat(command(script, "DEL")).isFalse();
        assertThat(command(script, "EXPIRE")).isFalse();
        assertThat(command(script, "KEYS")).isFalse();
    }

    private static boolean command(String script, String command) {
        return Pattern.compile("REDIS\\.CALL\\(['\"]" + command + "['\"]")
                .matcher(script)
                .find();
    }
}
