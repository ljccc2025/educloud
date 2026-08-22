package com.educloud.file.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M04 修复 A7：对象键工厂必须使用注入的 Clock 取日期（可测、可跨时区稳定），
 * 而不是系统默认 LocalDate.now()。
 */
class ObjectKeyFactoryTest {

    private static final String BUCKET = "educloud-files";
    private static final ContentTypePolicy POLICY =
            new ContentTypePolicy(java.util.List.of("image/png"), 10485760);

    @Test
    void createUsesInjectedClockDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-22T23:00:00Z"), ZoneOffset.UTC);
        ObjectKeyFactory factory = new ObjectKeyFactory(BUCKET, POLICY, clock);

        String key = factory.create("user-42", "image/png");

        assertThat(key).startsWith("educloud-files/user-42/20260822/");
        assertThat(key).endsWith(".png");
    }

    @Test
    void createUsesClockDateInItsOwnZone() {
        // 东京时区 2026-08-23 00:30 仍对应 UTC 2026-08-22 15:30：日期必须取 clock 所在时区。
        Clock tokyo = Clock.fixed(Instant.parse("2026-08-22T15:30:00Z"), ZoneOffset.ofHours(9));
        ObjectKeyFactory factory = new ObjectKeyFactory(BUCKET, POLICY, tokyo);

        assertThat(factory.create("user-42", "image/png"))
                .startsWith("educloud-files/user-42/20260823/");
    }
}
