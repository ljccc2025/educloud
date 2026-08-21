package com.educloud.common.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestContainerImagesTest {

    private static final String PRIVATE_REDIS =
            "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/redis:7.2.5-alpine";

    @Test
    void usesPinnedOfficialRedisImageWhenOverrideIsAbsent() {
        assertThat(TestContainerImages.redis(Map.<String, String>of()::get).asCanonicalNameString())
                .isEqualTo("redis:7.2.5-alpine");
    }

    @Test
    void acceptsPinnedPrivateRedisImage() {
        var environment = Map.of(TestContainerImages.REDIS_IMAGE_ENV, PRIVATE_REDIS);

        assertThat(TestContainerImages.redis(environment::get).asCanonicalNameString())
                .isEqualTo(PRIVATE_REDIS);
    }

    @Test
    void rejectsBlankRedisOverride() {
        var environment = Map.of(TestContainerImages.REDIS_IMAGE_ENV, " ");

        assertThatThrownBy(() -> TestContainerImages.redis(environment::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TestContainerImages.REDIS_IMAGE_ENV);
    }

    @Test
    void rejectsTaglessAndLatestRedisOverrides() {
        for (String value : new String[] {"private.example/redis", "private.example/redis:latest"}) {
            var environment = Map.of(TestContainerImages.REDIS_IMAGE_ENV, value);

            assertThatThrownBy(() -> TestContainerImages.redis(environment::get))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(TestContainerImages.REDIS_IMAGE_ENV);
        }
    }

    @Test
    void rejectsInvalidRedisOverride() {
        var environment = Map.of(
                TestContainerImages.REDIS_IMAGE_ENV, "https://private.example/redis:7.2.5");

        assertThatThrownBy(() -> TestContainerImages.redis(environment::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TestContainerImages.REDIS_IMAGE_ENV);
    }

    @Test
    void rejectsDigestOnlyRedisOverride() {
        var environment = Map.of(
                TestContainerImages.REDIS_IMAGE_ENV,
                "private.example/redis@sha256:" + "a".repeat(64));

        assertThatThrownBy(() -> TestContainerImages.redis(environment::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TestContainerImages.REDIS_IMAGE_ENV);
    }
}
