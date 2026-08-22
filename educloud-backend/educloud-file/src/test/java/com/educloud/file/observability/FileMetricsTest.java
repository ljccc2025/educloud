package com.educloud.file.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M04 任务 13：FileMetrics 计数行为测试（SimpleMeterRegistry）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 13 —— 上传会话创建/完成/失败、
 * 授权签发/拒绝、存储探测、清理删除。</p>
 */
class FileMetricsTest {

    @Test
    void countsUploadGrantStorageAndCleanupEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FileMetrics metrics = new FileMetrics(registry);

        metrics.recordUploadCreated();
        metrics.recordUploadCreated();
        metrics.recordUploadCompleted();
        metrics.recordUploadFailed();
        metrics.recordGrantGranted();
        metrics.recordGrantDenied();
        metrics.recordStorageTest();
        metrics.recordCleanupDeleted();

        assertCounter(registry, "educloud.file.upload_sessions_created_total", 2);
        assertCounter(registry, "educloud.file.upload_sessions_completed_total", 1);
        assertCounter(registry, "educloud.file.upload_sessions_failed_total", 1);
        assertCounter(registry, "educloud.file.grants_granted_total", 1);
        assertCounter(registry, "educloud.file.grants_denied_total", 1);
        assertCounter(registry, "educloud.file.storage_tests_total", 1);
        assertCounter(registry, "educloud.file.cleanup_deleted_files_total", 1);
    }

    private static void assertCounter(SimpleMeterRegistry registry, String name, double expected) {
        Counter counter = registry.get(name).counter();
        assertThat(counter.count()).isEqualTo(expected);
    }
}
