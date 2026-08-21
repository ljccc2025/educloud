package com.educloud.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestContainerImagesTest {

    private static final String PRIVATE_REDIS =
            "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/redis:7.2.5-alpine";
    private static final String PRIVATE_NACOS =
            "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nacos/nacos-server:v2.3.2";

    @Test
    void usesPinnedOfficialImagesWhenOverridesAreAbsent() {
        var environment = Map.<String, String>of();

        assertThat(TestContainerImages.redis(environment::get).asCanonicalNameString())
                .isEqualTo("redis:7.2.5-alpine");
        assertThat(TestContainerImages.nacos(environment::get).asCanonicalNameString())
                .isEqualTo("nacos/nacos-server:v2.3.2");
    }

    @Test
    void acceptsPinnedPrivateImagesIndependently() {
        var environment = Map.of(
                TestContainerImages.REDIS_IMAGE_ENV,
                PRIVATE_REDIS,
                TestContainerImages.NACOS_IMAGE_ENV,
                PRIVATE_NACOS);

        assertThat(TestContainerImages.redis(environment::get).asCanonicalNameString())
                .isEqualTo(PRIVATE_REDIS);
        assertThat(TestContainerImages.nacos(environment::get).asCanonicalNameString())
                .isEqualTo(PRIVATE_NACOS);
    }

    @Test
    void rejectsBlankRedisOverrideBeforeContainerCreation() {
        assertInvalid(TestContainerImages.REDIS_IMAGE_ENV, " ");
    }

    @Test
    void rejectsBlankNacosOverrideBeforeContainerCreation() {
        assertInvalid(TestContainerImages.NACOS_IMAGE_ENV, "");
    }

    @Test
    void rejectsTaglessAndLatestOverridesBeforeContainerCreation() {
        for (String variable : new String[] {
                TestContainerImages.REDIS_IMAGE_ENV, TestContainerImages.NACOS_IMAGE_ENV}) {
            assertInvalid(variable, "private.example/image");
            assertInvalid(variable, "private.example/image:latest");
        }
    }

    @Test
    void rejectsInvalidOverridesBeforeContainerCreation() {
        assertInvalid(TestContainerImages.REDIS_IMAGE_ENV, "https://private.example/redis:7.2.5");
        assertInvalid(TestContainerImages.NACOS_IMAGE_ENV, "https://private.example/nacos:v2.3.2");
    }

    @Test
    void rejectsDigestOnlyOverridesBeforeContainerCreation() {
        String digest = "private.example/image@sha256:" + "a".repeat(64);

        assertInvalid(TestContainerImages.REDIS_IMAGE_ENV, digest);
        assertInvalid(TestContainerImages.NACOS_IMAGE_ENV, digest);
    }

    private static void assertInvalid(String variable, String value) {
        var environment = Map.of(variable, value);

        assertThatThrownBy(() -> TestContainerImages.resolve(variable, "safe/image:1", environment::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(variable);
    }
}
