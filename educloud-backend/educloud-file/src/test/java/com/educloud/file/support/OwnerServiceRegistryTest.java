package com.educloud.file.support;

import com.educloud.file.exception.FileAccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M04 任务 10：内部调用方 clientId → ownerService 映射单元测试。
 *
 * <p>依据：M04 设计规格 6.2 节 —— user-service→user、course-service→course、
 * content-service→content、live-service→live；未知 clientId 抛
 * {@link FileAccessDeniedException}（控制器测试经 403 信封验证）。</p>
 */
class OwnerServiceRegistryTest {

    private final OwnerServiceRegistry registry = new OwnerServiceRegistry();

    @Test
    void mapsKnownClientIdsToOwnerServices() {
        assertThat(registry.require("user-service")).isEqualTo("user");
        assertThat(registry.require("course-service")).isEqualTo("course");
        // M05 任务 12：Course 以 educloud-course clientId 申请服务令牌（规格 §10 默认值）。
        assertThat(registry.require("educloud-course")).isEqualTo("course");
        assertThat(registry.require("content-service")).isEqualTo("content");
        assertThat(registry.require("live-service")).isEqualTo("live");
    }

    @Test
    void rejectsUnknownClientId() {
        assertThatThrownBy(() -> registry.require("evil-service"))
                .isInstanceOf(FileAccessDeniedException.class)
                .hasMessageContaining("evil-service");
    }
}
