package com.educloud.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RedisWorkerLeaseScriptsTest {

    @Test
    void acquireUsesRedisTimeNxPxAndAllThirtyTwoSlots() throws IOException {
        String script = script("acquire-worker.lua");

        assertThat(script)
                .contains("redis.call('TIME')")
                .contains("for worker = 0, 31")
                .contains("ARGV[1]")
                .contains("'NX'")
                .contains("'PX'")
                .contains("watermark")
                .doesNotContain("math.random");
    }

    @Test
    void renewChecksOwnerAndAdvancesWatermarkBeforeExtendingTtl() throws IOException {
        String script = script("renew-worker.lua");

        assertThat(script)
                .contains("redis.call('GET', KEYS[1]) ~= ARGV[1]")
                .contains("watermark")
                .contains("lastIssuedTimestamp")
                .contains("redis.call('PEXPIRE'")
                .doesNotContain("math.random");
    }

    @Test
    void releaseChecksOwnerAndAdvancesWatermarkBeforeDeletion() throws IOException {
        String script = script("release-worker.lua");

        assertThat(script)
                .contains("redis.call('GET', KEYS[1]) ~= ARGV[1]")
                .contains("watermark")
                .contains("lastIssuedTimestamp")
                .contains("redis.call('DEL', KEYS[1])")
                .doesNotContain("math.random");
    }

    private static String script(String name) throws IOException {
        return new ClassPathResource("com/educloud/common/id/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
