package com.educloud.file.support;

import com.educloud.file.config.FileProperties;
import com.educloud.file.dto.response.StorageStatusResponse;
import com.educloud.file.dto.response.StorageTestResponse;
import com.educloud.file.storage.StorageGateway;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * 存储状态与受限探测服务：status 用脱敏端点 + probe 结果；runTest 触发最小读写
 * 探测并写 STORAGE_TEST 审计。
 *
 * <p>依据：M04 设计规格 6.1/9 节 —— storage-status 与 storage-tests 都经
 * {@link StorageGateway#probe()}（put 临时对象→stat→delete，全程不抛异常）；
 * endpointMasked 只保留 scheme 与端口、隐藏 host；审计表 file_id 为 NOT NULL，
 * STORAGE_TEST 无关联文件，使用 0 哨兵值。accessKey/secretKey 永不进入响应。</p>
 */
@Service
public class StorageStatusService {

    public static final String PROVIDER_MINIO = "MINIO";

    /** file_access_audit.file_id 为 NOT NULL，STORAGE_TEST 无关联文件，用 0 哨兵。 */
    public static final long STORAGE_TEST_FILE_ID_SENTINEL = 0L;

    private final StorageGateway storageGateway;
    private final FileProperties properties;
    private final FileAccessAuditWriter auditWriter;
    private final Clock clock;

    public StorageStatusService(
            StorageGateway storageGateway,
            FileProperties properties,
            FileAccessAuditWriter auditWriter,
            Clock clock) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public StorageStatusResponse status() {
        StorageGateway.StorageProbeResult probe = storageGateway.probe();
        return new StorageStatusResponse(
                PROVIDER_MINIO,
                probe.ok(),
                maskEndpoint(properties.storage().endpoint()),
                clock.instant(),
                probe.errorCategory());
    }

    /** 最小读写探测 + STORAGE_TEST 审计；probe 成功/失败均记录。 */
    public StorageTestResponse runTest(Long userId) {
        long startedNanos = System.nanoTime();
        StorageGateway.StorageProbeResult probe = storageGateway.probe();
        long latencyMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        auditWriter.write(
                STORAGE_TEST_FILE_ID_SENTINEL,
                userId,
                FileAccessAuditWriter.ACTION_STORAGE_TEST,
                probe.ok() ? FileAccessAuditWriter.RESULT_SUCCESS : FileAccessAuditWriter.RESULT_FAILURE);
        return new StorageTestResponse(probe.ok(), latencyMs, probe.errorCategory());
    }

    /**
     * 脱敏存储端点：只保留 scheme 与端口，隐藏 host（如 http://127.0.0.1:9000
     * → http://***:9000）；无法解析时整体打码，避免泄漏内部主机名。
     */
    static String maskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "***";
        }
        try {
            URI uri = new URI(endpoint);
            String masked = uri.getScheme() == null ? "***" : uri.getScheme() + "://***";
            if (uri.getPort() != -1) {
                masked += ":" + uri.getPort();
            }
            return masked;
        } catch (URISyntaxException exception) {
            return "***";
        }
    }
}
