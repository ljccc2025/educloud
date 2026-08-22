package com.educloud.file.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * File 服务业务指标。依据：M04 设计规格第 12 节与实施计划任务 13 —— 上传会话生命周期
 * （created/completed/failed）、下载授权签发/拒绝、存储探测、清理删除。
 *
 * <p>低基数、无动态标签；Counter 注册名即任务清单中的 Prometheus 名称（Micrometer 1.12 的
 * PrometheusNamingConvention 对已以 {@code _total} 结尾的 Counter 不再追加后缀）。
 * 非 final：项目测试 MockMaker 为 subclass，服务接线测试需要 mock 本类做 verify 断言。</p>
 */
@Component
public class FileMetrics {

    private final Counter uploadSessionsCreated;
    private final Counter uploadSessionsCompleted;
    private final Counter uploadSessionsFailed;
    private final Counter grantsGranted;
    private final Counter grantsDenied;
    private final Counter storageTests;
    private final Counter cleanupDeletedFiles;

    public FileMetrics(MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.uploadSessionsCreated = Counter.builder("educloud.file.upload_sessions_created_total")
                .description("Upload sessions created").register(meterRegistry);
        this.uploadSessionsCompleted = Counter.builder("educloud.file.upload_sessions_completed_total")
                .description("Upload sessions completed").register(meterRegistry);
        this.uploadSessionsFailed = Counter.builder("educloud.file.upload_sessions_failed_total")
                .description("Upload sessions failed").register(meterRegistry);
        this.grantsGranted = Counter.builder("educloud.file.grants_granted_total")
                .description("Download grants issued").register(meterRegistry);
        this.grantsDenied = Counter.builder("educloud.file.grants_denied_total")
                .description("Download grant requests denied or unavailable").register(meterRegistry);
        this.storageTests = Counter.builder("educloud.file.storage_tests_total")
                .description("Storage tests triggered").register(meterRegistry);
        this.cleanupDeletedFiles = Counter.builder("educloud.file.cleanup_deleted_files_total")
                .description("Files deleted by cleanup").register(meterRegistry);
    }

    public void recordUploadCreated() {
        uploadSessionsCreated.increment();
    }

    public void recordUploadCompleted() {
        uploadSessionsCompleted.increment();
    }

    public void recordUploadFailed() {
        uploadSessionsFailed.increment();
    }

    public void recordGrantGranted() {
        grantsGranted.increment();
    }

    public void recordGrantDenied() {
        grantsDenied.increment();
    }

    public void recordStorageTest() {
        storageTests.increment();
    }

    public void recordCleanupDeleted() {
        cleanupDeletedFiles.increment();
    }
}
