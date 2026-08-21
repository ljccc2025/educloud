package com.educloud.gateway.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NacosPermissionResourcesTest {

    @Test
    void formatsConfigPermissionForNacosDefaultAuthPlugin() {
        assertThat(NacosPermissionResources.config("test-namespace", "TEST_GROUP", "gateway.yaml"))
                .isEqualTo("test-namespace:TEST_GROUP:config/gateway.yaml");
    }

    @Test
    void formatsNamingPermissionForNacosDefaultAuthPlugin() {
        assertThat(NacosPermissionResources.naming("test-namespace", "TEST_SERVICES", "user-service"))
                .isEqualTo("test-namespace:TEST_SERVICES:naming/user-service");
    }
}
