package com.educloud.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.file.config.FileProperties;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.entity.FileUploadSessionEntity;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.mapper.FileUploadSessionMapper;
import com.educloud.file.messaging.FileEventPublisher;
import com.educloud.file.observability.FileMetrics;
import com.educloud.file.storage.StorageGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 清理任务：未绑定 AVAILABLE 文件超保留期删除 + 过期上传会话清理。
 *
 * <p>依据：M04 设计规格第 7.4 节与计划任务 12 —— 入口均限量分批（batch-size）、
 * 幂等、单条失败记日志不中断批次。未绑定文件在事务内锁根 → 二次确认无活跃绑定 →
 * 乐观锁更新（updateById 返回 0 视为并发变更，静默跳过且不删对象）→ 发布
 * FileDeleted（Outbox，aggregateVersion=删除后根版本）→ afterCommit 删除 MinIO 对象；
 * 过期会话在事务内锁行置 EXPIRED，对象键无 AVAILABLE file_object 时 afterCommit 删除
 * MinIO 孤儿对象。第三步骤（{@link #cleanupDeletedObjects()}）兜底 afterCommit 删除
 * 失败的 DELETED 残留对象。与正常删除/绑定互踩由 file_object.version 乐观锁兜底
 * （与 FileObjectService/FileBindingService 同构）。</p>
 */
@Service
public class FileCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCleanupService.class);

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String REASON_UNBOUND = "cleanup-unbound";

    private final FileObjectMapper objectMapper;
    private final FileBindingMapper bindingMapper;
    private final FileUploadSessionMapper sessionMapper;
    private final StorageGateway storageGateway;
    private final FileEventPublisher eventPublisher;
    private final FileProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final FileMetrics metrics;

    public FileCleanupService(
            FileObjectMapper objectMapper,
            FileBindingMapper bindingMapper,
            FileUploadSessionMapper sessionMapper,
            StorageGateway storageGateway,
            FileEventPublisher eventPublisher,
            FileProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager,
            FileMetrics metrics) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.bindingMapper = Objects.requireNonNull(bindingMapper, "bindingMapper");
        this.sessionMapper = Objects.requireNonNull(sessionMapper, "sessionMapper");
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** 定时入口：未绑定文件 → 过期会话 → DELETED 残留对象兜底（三者互不依赖，各自分批容错）。 */
    @Scheduled(fixedDelayString = "${educloud.file.cleanup.interval:60000}")
    public void cleanup() {
        cleanupUnboundFiles();
        cleanupExpiredSessions();
        cleanupDeletedObjects();
    }

    /**
     * 清理超保留期仍未绑定的 AVAILABLE 文件：事务内锁根 → 二次确认无活跃绑定 →
     * 乐观锁置 DELETED（0 行命中即并发变更，静默跳过）→ FileDeleted → afterCommit 删对象。
     */
    public void cleanupUnboundFiles() {
        FileProperties.Cleanup cleanup = properties.cleanup();
        Instant cutoff = clock.instant().minus(cleanup.unboundRetention());
        List<FileObjectEntity> candidates =
                objectMapper.selectUnboundCandidates(cutoff, cleanup.batchSize());
        for (FileObjectEntity candidate : candidates) {
            try {
                transactionTemplate.executeWithoutResult(status -> cleanupUnboundFile(candidate.getId()));
            } catch (Exception failure) {
                LOGGER.warn("未绑定文件清理失败，跳过 fileId={}", candidate.getId(), failure);
            }
        }
    }

    /**
     * 清理过期 PENDING 会话：事务内锁行幂等置 EXPIRED；对象键无 AVAILABLE file_object
     * 时删除 MinIO 孤儿对象（会话已登记为文件对象的由 file_object 生命周期管理，跳过）。
     */
    public void cleanupExpiredSessions() {
        FileProperties.Cleanup cleanup = properties.cleanup();
        Instant now = clock.instant();
        List<FileUploadSessionEntity> sessions = sessionMapper.selectExpired(now, cleanup.batchSize());
        for (FileUploadSessionEntity session : sessions) {
            try {
                transactionTemplate.executeWithoutResult(status -> cleanupExpiredSession(session.getId()));
            } catch (Exception failure) {
                LOGGER.warn("过期会话清理失败，跳过 sessionId={}", session.getId(), failure);
            }
        }
    }

    private void cleanupUnboundFile(Long fileId) {
        FileObjectEntity root = objectMapper.selectByIdForUpdate(fileId);
        if (root == null || !STATUS_AVAILABLE.equals(root.getStatus())) {
            return; // 已被正常删除流程处理或状态不允许，幂等跳过
        }
        if (bindingMapper.countActiveByFileId(fileId) > 0) {
            return; // 二次确认发现活跃绑定，跳过
        }
        // 先提交乐观锁更新再删对象：0 行命中（并发绑定/删除已变更版本）时对象保持不动。
        int versionAfterDelete = root.getVersion() + 1;
        root.setStatus(STATUS_DELETED);
        root.setDeletedAt(clock.instant());
        int updated = objectMapper.updateById(root);
        if (updated == 0) {
            LOGGER.debug("未绑定文件版本冲突，跳过 fileId={}", fileId);
            return;
        }
        // 清理事件无属主；aggregateVersion=删除后根版本（与 FileObjectService.deleteIfUnbound 一致）。
        eventPublisher.fileDeleted(
                fileId, root.getObjectKey(), null, null, null, REASON_UNBOUND, versionAfterDelete);
        // 对象删除在事务提交后执行：回滚时不删对象，提交后删除失败由 cleanupDeletedObjects 兜底。
        deleteObjectAfterCommit(root.getBucket(), root.getObjectKey());
    }

    private void cleanupExpiredSession(Long sessionId) {
        FileUploadSessionEntity session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || !STATUS_PENDING.equals(session.getStatus())) {
            return; // 幂等：仅 PENDING 会话可置 EXPIRED
        }
        session.setStatus(STATUS_EXPIRED);
        int updated = sessionMapper.updateById(session);
        if (updated == 0) {
            return; // 行已并发变更（如已被其他清理置 EXPIRED），不注册删除回调
        }
        boolean hasAvailableObject = objectMapper.selectCount(
                new LambdaQueryWrapper<FileObjectEntity>()
                        .eq(FileObjectEntity::getObjectKey, session.getObjectKey())
                        .eq(FileObjectEntity::getStatus, STATUS_AVAILABLE)) > 0;
        if (!hasAvailableObject) {
            deleteObjectAfterCommit(session.getBucket(), session.getObjectKey());
        }
    }

    /**
     * 清理兜底：afterCommit 删除失败后仍处于 DELETED 的残留对象（超保留期）补删 MinIO
     * 对象。DB 已是 DELETED，不回写；删除幂等（MinIO 对不存在对象不报错）。
     */
    public void cleanupDeletedObjects() {
        FileProperties.Cleanup cleanup = properties.cleanup();
        Instant cutoff = clock.instant().minus(cleanup.unboundRetention());
        List<FileObjectEntity> candidates =
                objectMapper.selectDeletedCandidates(cutoff, cleanup.batchSize());
        for (FileObjectEntity candidate : candidates) {
            try {
                storageGateway.deleteObject(candidate.getBucket(), candidate.getObjectKey());
                metrics.recordCleanupDeleted();
            } catch (RuntimeException failure) {
                LOGGER.error("DELETED 残留对象删除失败，下轮重试: fileId={}, bucket={}, object={}",
                        candidate.getId(), candidate.getBucket(), candidate.getObjectKey(), failure);
            }
        }
    }

    /** 事务提交后删除 MinIO 对象；无事务上下文（直接调用）时立即删除。 */
    private void deleteObjectAfterCommit(String bucket, String objectKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeDeleteObject(bucket, objectKey);
                }
            });
        } else {
            safeDeleteObject(bucket, objectKey);
        }
    }

    private void safeDeleteObject(String bucket, String objectKey) {
        try {
            storageGateway.deleteObject(bucket, objectKey);
            metrics.recordCleanupDeleted();
        } catch (RuntimeException failure) {
            // DB 已提交，不回滚；残留对象由 cleanupDeletedObjects 兜底。
            LOGGER.error("清理删除对象失败（DB 已提交，待兜底）: bucket={}, object={}",
                    bucket, objectKey, failure);
        }
    }
}
