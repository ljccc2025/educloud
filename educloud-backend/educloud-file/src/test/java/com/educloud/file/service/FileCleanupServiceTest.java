package com.educloud.file.service;

import com.educloud.file.config.FileProperties;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.entity.FileUploadSessionEntity;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.mapper.FileUploadSessionMapper;
import com.educloud.file.messaging.FileEventPublisher;
import com.educloud.file.observability.FileMetrics;
import com.educloud.file.storage.StorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 12：清理任务单元测试（mock Mapper/StorageGateway/事件发布器）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 12 —— 未绑定 AVAILABLE 文件超保留期后
 * 二次确认无活跃绑定才删除（事务 + 乐观锁，updateById 返回 0 时静默跳过）；过期 PENDING
 * 会话置 EXPIRED，对象键无 AVAILABLE file_object 时删除 MinIO 孤儿对象；单条失败记日志
 * 不中断批次。事务以 mock PlatformTransactionManager 驱动 TransactionTemplate（每项独立事务边界）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileCleanupServiceTest {

    private static final long FILE_ID_1 = 1001L;
    private static final long FILE_ID_2 = 1002L;
    private static final long SESSION_ID_1 = 201L;
    private static final long SESSION_ID_2 = 202L;
    private static final String BUCKET = "educloud-files";
    private static final String OBJECT_KEY_1 = "educloud-files/user-42/20260822/abc.png";
    private static final String OBJECT_KEY_2 = "educloud-files/user-42/20260822/def.pdf";
    private static final String CLEANUP_REASON = "cleanup-unbound";
    private static final int BATCH_SIZE = 50;
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final Duration RETENTION = Duration.ofHours(24);

    @Mock
    private FileObjectMapper objectMapper;
    @Mock
    private FileBindingMapper bindingMapper;
    @Mock
    private FileUploadSessionMapper sessionMapper;
    @Mock
    private StorageGateway storageGateway;
    @Mock
    private FileEventPublisher eventPublisher;
    @Mock
    private FileMetrics metrics;

    private FileCleanupService service;
    private FileProperties properties;

    @BeforeEach
    void setUp() {
        properties = fileProperties();
        service = new FileCleanupService(
                objectMapper,
                bindingMapper,
                sessionMapper,
                storageGateway,
                eventPublisher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new TestTransactionManager(),
                metrics);
    }

    @Test
    void filesWithinRetentionAreNotCleaned() {
        Instant cutoff = NOW.minus(RETENTION);
        when(objectMapper.selectUnboundCandidates(cutoff, BATCH_SIZE)).thenReturn(List.of());

        service.cleanupUnboundFiles();

        // 保留期内文件由 SQL（uploaded_at < cutoff）排除，服务只按保留期边界取候选。
        verify(objectMapper).selectUnboundCandidates(cutoff, BATCH_SIZE);
        verify(bindingMapper, never()).countActiveByFileId(anyLong());
        verify(storageGateway, never()).deleteObject(anyString(), anyString());
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
        verify(eventPublisher, never()).fileDeleted(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void expiredUnboundFileDeletesObjectMarksRowDeletedAndPublishesEvent() {
        FileObjectEntity root = availableFile(FILE_ID_1, OBJECT_KEY_1);
        when(objectMapper.selectUnboundCandidates(NOW.minus(RETENTION), BATCH_SIZE))
                .thenReturn(List.of(root));
        when(objectMapper.selectByIdForUpdate(FILE_ID_1)).thenReturn(root);
        when(bindingMapper.countActiveByFileId(FILE_ID_1)).thenReturn(0L);
        when(objectMapper.updateById(any(FileObjectEntity.class))).thenReturn(1);

        service.cleanupUnboundFiles();

        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY_1);
        ArgumentCaptor<FileObjectEntity> captor =
                ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(objectMapper).updateById(captor.capture());
        FileObjectEntity updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("DELETED");
        assertThat(updated.getDeletedAt()).isEqualTo(NOW);
        // 传给 updateById 的实体 version 保持旧值，由乐观锁拦截器生成 WHERE version=旧。
        assertThat(updated.getVersion()).isEqualTo(1);
        // 清理事件无属主：ownerService/ownerType/ownerId 传 null；aggregateVersion=删除后版本 2。
        verify(eventPublisher).fileDeleted(
                eq(FILE_ID_1), eq(OBJECT_KEY_1), isNull(), isNull(), isNull(),
                eq(CLEANUP_REASON), eq(2L));
        verify(metrics).recordCleanupDeleted();
    }

    @Test
    void expiredFileWithActiveBindingIsSkipped() {
        FileObjectEntity root = availableFile(FILE_ID_1, OBJECT_KEY_1);
        when(objectMapper.selectUnboundCandidates(NOW.minus(RETENTION), BATCH_SIZE))
                .thenReturn(List.of(root));
        when(objectMapper.selectByIdForUpdate(FILE_ID_1)).thenReturn(root);
        when(bindingMapper.countActiveByFileId(FILE_ID_1)).thenReturn(1L);

        service.cleanupUnboundFiles();

        verify(storageGateway, never()).deleteObject(anyString(), anyString());
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
        verify(eventPublisher, never()).fileDeleted(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong());
        verify(metrics, never()).recordCleanupDeleted();
    }

    @Test
    void versionConflictOnSecondCheckSkipsWithoutError() {
        FileObjectEntity root = availableFile(FILE_ID_1, OBJECT_KEY_1);
        when(objectMapper.selectUnboundCandidates(NOW.minus(RETENTION), BATCH_SIZE))
                .thenReturn(List.of(root));
        when(objectMapper.selectByIdForUpdate(FILE_ID_1)).thenReturn(root);
        when(bindingMapper.countActiveByFileId(FILE_ID_1)).thenReturn(0L);
        // 二次确认后并发绑定导致版本变化：乐观锁 0 行命中 → 静默跳过，不抛错、不删对象。
        when(objectMapper.updateById(any(FileObjectEntity.class))).thenReturn(0);

        assertThatCode(service::cleanupUnboundFiles).doesNotThrowAnyException();

        verify(objectMapper).updateById(root);
        verify(storageGateway, never()).deleteObject(anyString(), anyString());
        verify(eventPublisher, never()).fileDeleted(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void expiredSessionsMarkedExpiredAndOrphanObjectsDeleted() {
        FileUploadSessionEntity orphan = pendingSession(SESSION_ID_1, OBJECT_KEY_1);
        FileUploadSessionEntity completed = pendingSession(SESSION_ID_2, OBJECT_KEY_2);
        when(sessionMapper.selectExpired(NOW, BATCH_SIZE)).thenReturn(List.of(orphan, completed));
        when(sessionMapper.selectByIdForUpdate(SESSION_ID_1)).thenReturn(orphan);
        when(sessionMapper.selectByIdForUpdate(SESSION_ID_2)).thenReturn(completed);
        when(sessionMapper.updateById(any(FileUploadSessionEntity.class))).thenReturn(1);
        // 第一个会话对象键无 AVAILABLE file_object（删孤儿对象），第二个已登记（跳过）。
        when(objectMapper.selectCount(any())).thenReturn(0L, 1L);

        service.cleanupExpiredSessions();

        ArgumentCaptor<FileUploadSessionEntity> captor =
                ArgumentCaptor.forClass(FileUploadSessionEntity.class);
        verify(sessionMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FileUploadSessionEntity::getStatus)
                .containsExactly("EXPIRED", "EXPIRED");
        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY_1);
        verify(storageGateway, never()).deleteObject(BUCKET, OBJECT_KEY_2);
        verify(metrics).recordCleanupDeleted();
    }

    @Test
    void singleFailureDoesNotAbortTheBatch() {
        FileObjectEntity first = availableFile(FILE_ID_1, OBJECT_KEY_1);
        FileObjectEntity second = availableFile(FILE_ID_2, OBJECT_KEY_2);
        when(objectMapper.selectUnboundCandidates(NOW.minus(RETENTION), BATCH_SIZE))
                .thenReturn(List.of(first, second));
        when(objectMapper.selectByIdForUpdate(FILE_ID_1)).thenReturn(first);
        when(objectMapper.selectByIdForUpdate(FILE_ID_2)).thenReturn(second);
        when(bindingMapper.countActiveByFileId(anyLong())).thenReturn(0L);
        when(objectMapper.updateById(any(FileObjectEntity.class))).thenReturn(1);
        doThrow(new IllegalStateException("minio unavailable"))
                .when(storageGateway).deleteObject(eq(BUCKET), eq(OBJECT_KEY_1));

        assertThatCode(service::cleanupUnboundFiles).doesNotThrowAnyException();

        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY_1);
        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY_2);
        // DB 更新与 FileDeleted（Outbox）在事务内已提交；afterCommit 删除失败不回滚事件。
        verify(eventPublisher).fileDeleted(
                eq(FILE_ID_1), eq(OBJECT_KEY_1), isNull(), isNull(), isNull(),
                eq(CLEANUP_REASON), eq(2L));
        verify(eventPublisher).fileDeleted(
                eq(FILE_ID_2), eq(OBJECT_KEY_2), isNull(), isNull(), isNull(),
                eq(CLEANUP_REASON), eq(2L));
        verify(eventPublisher, times(2)).fileDeleted(anyLong(), anyString(), any(),
                any(), any(), anyString(), anyLong());
        // 删除成功项计数一次；afterCommit 删除失败的项不计数（A8 兜底重试）。
        verify(metrics, times(1)).recordCleanupDeleted();
    }

    @Test
    void cleanupDeletedObjectsRetriesStorageDeletionForDelayedRows() {
        FileObjectEntity stale = deletedFile(FILE_ID_1, OBJECT_KEY_1);
        when(objectMapper.selectDeletedCandidates(NOW.minus(RETENTION), BATCH_SIZE))
                .thenReturn(List.of(stale));

        service.cleanupDeletedObjects();

        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY_1);
        verify(metrics).recordCleanupDeleted();
        // A8 只补删 MinIO 对象，不回写 DB（DB 已是 DELETED）。
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
    }

    @Test
    void cleanupDeletedObjectsContinuesWhenStorageDeleteFails() {
        FileObjectEntity stale = deletedFile(FILE_ID_1, OBJECT_KEY_1);
        when(objectMapper.selectDeletedCandidates(NOW.minus(RETENTION), BATCH_SIZE))
                .thenReturn(List.of(stale));
        doThrow(new IllegalStateException("minio unavailable"))
                .when(storageGateway).deleteObject(BUCKET, OBJECT_KEY_1);

        assertThatCode(service::cleanupDeletedObjects).doesNotThrowAnyException();

        verify(metrics, never()).recordCleanupDeleted();
    }

    @Test
    void expiredUnboundFileDoesNotDeleteObjectWhenTransactionRollsBack() {
        FileObjectEntity root = availableFile(FILE_ID_1, OBJECT_KEY_1);
        when(objectMapper.selectUnboundCandidates(NOW.minus(RETENTION), BATCH_SIZE))
                .thenReturn(List.of(root));
        when(objectMapper.selectByIdForUpdate(FILE_ID_1)).thenReturn(root);
        when(bindingMapper.countActiveByFileId(FILE_ID_1)).thenReturn(0L);
        when(objectMapper.updateById(any(FileObjectEntity.class))).thenReturn(1);
        // Outbox 写入失败 → 事务回滚 → afterCommit 不触发，MinIO 对象保留。
        doThrow(new IllegalStateException("outbox down"))
                .when(eventPublisher).fileDeleted(anyLong(), anyString(), any(),
                        any(), any(), anyString(), anyLong());

        assertThatCode(service::cleanupUnboundFiles).doesNotThrowAnyException();

        verify(storageGateway, never()).deleteObject(anyString(), anyString());
        verify(metrics, never()).recordCleanupDeleted();
    }

    private FileObjectEntity deletedFile(long fileId, String objectKey) {
        FileObjectEntity root = new FileObjectEntity();
        root.setId(fileId);
        root.setObjectKey(objectKey);
        root.setBucket(BUCKET);
        root.setStatus("DELETED");
        root.setDeletedAt(NOW.minus(Duration.ofDays(2)));
        root.setVersion(2);
        return root;
    }

    private FileObjectEntity availableFile(long fileId, String objectKey) {
        FileObjectEntity root = new FileObjectEntity();
        root.setId(fileId);
        root.setObjectKey(objectKey);
        root.setBucket(BUCKET);
        root.setStatus("AVAILABLE");
        root.setUploadedAt(NOW.minus(Duration.ofDays(2)));
        root.setVersion(1);
        return root;
    }

    private FileUploadSessionEntity pendingSession(long sessionId, String objectKey) {
        FileUploadSessionEntity session = new FileUploadSessionEntity();
        session.setId(sessionId);
        session.setUploaderId(42L);
        session.setObjectKey(objectKey);
        session.setBucket(BUCKET);
        session.setStatus("PENDING");
        session.setExpiresAt(NOW.minusSeconds(1));
        session.setVersion(1);
        return session;
    }

    private FileProperties fileProperties() {
        return new FileProperties(
                new FileProperties.Storage("http://127.0.0.1:9000", "ak", "sk", BUCKET),
                new FileProperties.Upload(
                        10 * 1024 * 1024,
                        List.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(15)),
                new FileProperties.DownloadGrant(
                        Duration.ofMinutes(5), Duration.ofMinutes(15), List.of("PROFILE_AVATAR", "PUBLIC_CATALOG")),
                new FileProperties.Cleanup(RETENTION, Duration.ofMinutes(15), BATCH_SIZE),
                new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                new FileProperties.Internal("bootstrap", List.of("user-service"), "educloud-file"),
                new FileProperties.Jwt("file:/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                    "local");
    }
}
