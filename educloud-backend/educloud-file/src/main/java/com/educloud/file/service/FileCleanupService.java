package com.educloud.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.file.config.FileProperties;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.entity.FileUploadSessionEntity;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.mapper.FileUploadSessionMapper;
import com.educloud.file.messaging.FileEventPublisher;
import com.educloud.file.storage.StorageGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 清理任务：未绑定 AVAILABLE 文件超保留期删除 + 过期上传会话清理。
 *
 * <p>依据：M04 设计规格第 7.4 节与计划任务 12 —— 两个入口均限量分批（batch-size）、
 * 幂等、单条失败记日志不中断批次。未绑定文件在事务内锁根 → 二次确认无活跃绑定 →
 * 乐观锁更新（updateById 返回 0 视为并发变更，静默跳过且不删对象）→ 删 MinIO 对象 →
 * 发布 FileDeleted（aggregateVersion=删除后根版本）；过期会话在事务内锁行置 EXPIRED，
 * 对象键无 AVAILABLE file_object 时删除 MinIO 孤儿对象。与正常删除/绑定互踩由
 * file_object.version 乐观锁兜底（与 FileObjectService/FileBindingService 同构）。</p>
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

    public FileCleanupService(
            FileObjectMapper objectMapper,
            FileBindingMapper bindingMapper,
            FileUploadSessionMapper sessionMapper,
            StorageGateway storageGateway,
            FileEventPublisher eventPublisher,
            FileProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.bindingMapper = Objects.requireNonNull(bindingMapper, "bindingMapper");
        this.sessionMapper = Objects.requireNonNull(sessionMapper, "sessionMapper");
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    /** 定时入口：先清未绑定文件，再清过期会话（两者互不依赖，各自分批容错）。 */
    @Scheduled(fixedDelayString = "${educloud.file.cleanup.interval:60000}")
    public void cleanup() {
        cleanupUnboundFiles();
        cleanupExpiredSessions();
    }

    /**
     * 清理超保留期仍未绑定的 AVAILABLE 文件：事务内锁根 → 二次确认无活跃绑定 →
     * 乐观锁置 DELETED（0 行命中即并发变更，静默跳过）→ 删 MinIO 对象 → FileDeleted。
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
        storageGateway.deleteObject(root.getBucket(), root.getObjectKey());
        // 清理事件无属主；aggregateVersion=删除后根版本（与 FileObjectService.deleteIfUnbound 一致）。
        eventPublisher.fileDeleted(
                fileId, root.getObjectKey(), null, null, null, REASON_UNBOUND, versionAfterDelete);
    }

    private void cleanupExpiredSession(Long sessionId) {
        FileUploadSessionEntity session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || !STATUS_PENDING.equals(session.getStatus())) {
            return; // 幂等：仅 PENDING 会话可置 EXPIRED
        }
        session.setStatus(STATUS_EXPIRED);
        sessionMapper.updateById(session);
        boolean hasAvailableObject = objectMapper.selectCount(
                new LambdaQueryWrapper<FileObjectEntity>()
                        .eq(FileObjectEntity::getObjectKey, session.getObjectKey())
                        .eq(FileObjectEntity::getStatus, STATUS_AVAILABLE)) > 0;
        if (!hasAvailableObject) {
            storageGateway.deleteObject(session.getBucket(), session.getObjectKey());
        }
    }
}
