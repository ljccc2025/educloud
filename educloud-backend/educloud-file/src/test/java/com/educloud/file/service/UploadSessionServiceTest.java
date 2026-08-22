package com.educloud.file.service;

import com.educloud.file.config.FileProperties;
import com.educloud.file.dto.request.CreateUploadSessionRequest;
import com.educloud.file.dto.response.UploadSessionResponse;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.entity.FileUploadSessionEntity;
import com.educloud.file.exception.UploadNotVerifiedException;
import com.educloud.file.exception.UploadSessionAccessDeniedException;
import com.educloud.file.exception.UploadSessionExpiredException;
import com.educloud.file.exception.UploadSessionNotFoundException;
import com.educloud.file.exception.UploadSessionStateException;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.mapper.FileUploadSessionMapper;
import com.educloud.file.storage.FileTooLargeException;
import com.educloud.file.storage.FileTypeNotAllowedException;
import com.educloud.file.storage.StorageGateway;
import com.educloud.file.support.ContentTypePolicy;
import com.educloud.file.support.ObjectKeyFactory;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 4：上传会话状态机单元测试（mock StorageGateway + Mapper，不依赖真实存储/DB）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 4 —— create 生成对象键 + presigned PUT URL +
 * 落库 PENDING；类型/大小拒绝；complete 校验存在性/大小/类型、计算 SHA-256、落 AVAILABLE
 * 对象并置会话 COMPLETED；过期/非 PENDING/对象缺失拒绝；expireOverdue 批量过期。</p>
 */
@ExtendWith(MockitoExtension.class)
class UploadSessionServiceTest {

    private static final long UPLOADER_ID = 42L;
    private static final long SESSION_ID = 77L;
    private static final String BUCKET = "educloud-files";
    private static final String OBJECT_KEY = "educloud-files/user-42/20260822/123e4567-e89b-12d3-a456-426614174000.png";
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");

    @Mock
    private FileUploadSessionMapper sessionMapper;
    @Mock
    private FileObjectMapper objectMapper;
    @Mock
    private StorageGateway storageGateway;

    private UploadSessionService service;
    private ContentTypePolicy contentTypePolicy;
    private FileProperties properties;

    @BeforeEach
    void setUp() {
        properties = fileProperties();
        contentTypePolicy = new ContentTypePolicy(
                properties.upload().allowedContentTypes(), properties.upload().maxSizeBytes());
        ObjectKeyFactory objectKeyFactory =
                new ObjectKeyFactory(properties.storage().bucket(), contentTypePolicy);
        service = new UploadSessionService(
                sessionMapper,
                objectMapper,
                storageGateway,
                objectKeyFactory,
                contentTypePolicy,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createGeneratesObjectKeyAndPersistsPendingSession() {
        when(storageGateway.presignedPutUrl(anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn("https://minio.example/put");

        UploadSessionResponse response = service.create(
                UPLOADER_ID, new CreateUploadSessionRequest("image/png", 2048L, "avatar.png"));

        assertThat(response.uploadUrl()).isEqualTo("https://minio.example/put");
        assertThat(response.expiresInSeconds()).isEqualTo(300);

        ArgumentCaptor<FileUploadSessionEntity> captor =
                ArgumentCaptor.forClass(FileUploadSessionEntity.class);
        verify(sessionMapper).insert(captor.capture());
        FileUploadSessionEntity session = captor.getValue();

        assertThat(session.getId()).isNotNull();
        assertThat(response.sessionId()).isEqualTo(session.getId());
        assertThat(session.getObjectKey())
                .matches("educloud-files/user-42/\\d{8}/[0-9a-f-]{36}\\.png");
        assertThat(session.getBucket()).isEqualTo(BUCKET);
        assertThat(session.getUploaderId()).isEqualTo(UPLOADER_ID);
        assertThat(session.getOriginalName()).isEqualTo("avatar.png");
        assertThat(session.getContentType()).isEqualTo("image/png");
        assertThat(session.getExpectedSizeBytes()).isEqualTo(2048L);
        assertThat(session.getStatus()).isEqualTo("PENDING");
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
        assertThat(session.getPutUrlExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(session.getVersion()).isEqualTo(1);
        verify(storageGateway).presignedPutUrl(
                eq(BUCKET), eq(session.getObjectKey()), eq("image/png"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void createRejectsContentTypeOutsideWhitelist() {
        assertThatThrownBy(() -> service.create(
                UPLOADER_ID, new CreateUploadSessionRequest("application/octet-stream", null, null)))
                .isInstanceOf(FileTypeNotAllowedException.class);

        verify(sessionMapper, never()).insert(any(FileUploadSessionEntity.class));
        verify(storageGateway, never())
                .presignedPutUrl(anyString(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void createRejectsExpectedSizeOverLimit() {
        assertThatThrownBy(() -> service.create(
                UPLOADER_ID, new CreateUploadSessionRequest("image/png", MAX_SIZE_BYTES + 1, null)))
                .isInstanceOf(FileTooLargeException.class);

        verify(sessionMapper, never()).insert(any(FileUploadSessionEntity.class));
        verify(storageGateway, never())
                .presignedPutUrl(anyString(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void completeVerifiesObjectComputesSha256AndPersistsAvailableObject() {
        FileUploadSessionEntity session = pendingSession();
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(storageGateway.stat(BUCKET, OBJECT_KEY))
                .thenReturn(new StorageGateway.ObjectStat(true, 2048L, "image/png"));
        when(storageGateway.sha256(BUCKET, OBJECT_KEY, (int) MAX_SIZE_BYTES))
                .thenReturn("abc123");

        FileObjectEntity result = service.complete(UPLOADER_ID, SESSION_ID);

        ArgumentCaptor<FileObjectEntity> objectCaptor =
                ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(objectMapper).insert(objectCaptor.capture());
        FileObjectEntity object = objectCaptor.getValue();
        assertThat(object.getId()).isNotNull();
        assertThat(object.getObjectKey()).isEqualTo(OBJECT_KEY);
        assertThat(object.getBucket()).isEqualTo(BUCKET);
        assertThat(object.getOriginalName()).isEqualTo("avatar.png");
        assertThat(object.getContentType()).isEqualTo("image/png");
        assertThat(object.getSizeBytes()).isEqualTo(2048L);
        assertThat(object.getSha256()).isEqualTo("abc123");
        assertThat(object.getStatus()).isEqualTo("AVAILABLE");
        assertThat(object.getUploaderId()).isEqualTo(UPLOADER_ID);
        assertThat(object.getUploadedAt()).isEqualTo(NOW);
        assertThat(object.getDeletedAt()).isNull();
        assertThat(object.getVersion()).isEqualTo(1);
        assertThat(result).isSameAs(object);

        verify(sessionMapper).updateById(org.mockito.ArgumentMatchers.<FileUploadSessionEntity>argThat(s ->
                "COMPLETED".equals(s.getStatus()) && s.getId().equals(SESSION_ID)));
    }

    @Test
    void completeRejectsWhenObjectDoesNotExist() {
        FileUploadSessionEntity session = pendingSession();
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(storageGateway.stat(BUCKET, OBJECT_KEY))
                .thenReturn(new StorageGateway.ObjectStat(false, 0, null));

        assertThatThrownBy(() -> service.complete(UPLOADER_ID, SESSION_ID))
                .isInstanceOf(UploadNotVerifiedException.class);

        verify(objectMapper, never()).insert(any(FileObjectEntity.class));
        verify(sessionMapper, never()).updateById(any(FileUploadSessionEntity.class));
    }

    @Test
    void completeMarksExpiredSessionAsExpiredAndRejects() {
        FileUploadSessionEntity session = pendingSession();
        session.setExpiresAt(NOW.minusSeconds(1));
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);

        assertThatThrownBy(() -> service.complete(UPLOADER_ID, SESSION_ID))
                .isInstanceOf(UploadSessionExpiredException.class);

        verify(sessionMapper).updateById(org.mockito.ArgumentMatchers.<FileUploadSessionEntity>argThat(s ->
                "EXPIRED".equals(s.getStatus()) && s.getId().equals(SESSION_ID)));
        verify(storageGateway, never()).stat(anyString(), anyString());
        verify(objectMapper, never()).insert(any(FileObjectEntity.class));
    }

    @Test
    void completeRejectsSessionNotInPendingState() {
        FileUploadSessionEntity session = pendingSession();
        session.setStatus("COMPLETED");
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);

        assertThatThrownBy(() -> service.complete(UPLOADER_ID, SESSION_ID))
                .isInstanceOf(UploadSessionStateException.class);

        verify(sessionMapper, never()).updateById(any(FileUploadSessionEntity.class));
        verify(storageGateway, never()).stat(anyString(), anyString());
        verify(objectMapper, never()).insert(any(FileObjectEntity.class));
    }

    @Test
    void expireOverdueMarksOverduePendingSessionsAsExpired() {
        FileUploadSessionEntity overdue1 = pendingSession();
        overdue1.setId(1L);
        FileUploadSessionEntity overdue2 = pendingSession();
        overdue2.setId(2L);
        when(sessionMapper.selectList(any())).thenReturn(List.of(overdue1, overdue2));

        service.expireOverdue(Duration.ofMinutes(15));

        ArgumentCaptor<FileUploadSessionEntity> captor =
                ArgumentCaptor.forClass(FileUploadSessionEntity.class);
        verify(sessionMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FileUploadSessionEntity::getStatus)
                .containsExactly("EXPIRED", "EXPIRED");
    }

    @Test
    void completeRejectsSessionNotFound() {
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.complete(UPLOADER_ID, SESSION_ID))
                .isInstanceOf(UploadSessionNotFoundException.class);

        verify(storageGateway, never()).stat(anyString(), anyString());
    }

    @Test
    void completeRejectsForeignUploader() {
        FileUploadSessionEntity session = pendingSession();
        session.setUploaderId(999L);
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);

        assertThatThrownBy(() -> service.complete(UPLOADER_ID, SESSION_ID))
                .isInstanceOf(UploadSessionAccessDeniedException.class);

        verify(storageGateway, never()).stat(anyString(), anyString());
        verify(objectMapper, never()).insert(any(FileObjectEntity.class));
    }

    @Test
    void completeRejectsActualSizeOverLimit() {
        FileUploadSessionEntity session = pendingSession();
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(storageGateway.stat(BUCKET, OBJECT_KEY))
                .thenReturn(new StorageGateway.ObjectStat(true, MAX_SIZE_BYTES + 1, "image/png"));

        assertThatThrownBy(() -> service.complete(UPLOADER_ID, SESSION_ID))
                .isInstanceOf(FileTooLargeException.class);

        verify(objectMapper, never()).insert(any(FileObjectEntity.class));
        verify(sessionMapper, never()).updateById(any(FileUploadSessionEntity.class));
    }

    @Test
    void completeRejectsStatContentTypeOutsideWhitelist() {
        FileUploadSessionEntity session = pendingSession();
        when(sessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(storageGateway.stat(BUCKET, OBJECT_KEY))
                .thenReturn(new StorageGateway.ObjectStat(true, 2048L, "application/octet-stream"));

        assertThatThrownBy(() -> service.complete(UPLOADER_ID, SESSION_ID))
                .isInstanceOf(FileTypeNotAllowedException.class);

        verify(objectMapper, never()).insert(any(FileObjectEntity.class));
        verify(sessionMapper, never()).updateById(any(FileUploadSessionEntity.class));
    }

    private FileUploadSessionEntity pendingSession() {
        FileUploadSessionEntity session = new FileUploadSessionEntity();
        session.setId(SESSION_ID);
        session.setUploaderId(UPLOADER_ID);
        session.setObjectKey(OBJECT_KEY);
        session.setBucket(BUCKET);
        session.setOriginalName("avatar.png");
        session.setContentType("image/png");
        session.setExpectedSizeBytes(2048L);
        session.setStatus("PENDING");
        session.setPutUrlExpiresAt(NOW.plus(Duration.ofMinutes(5)));
        session.setExpiresAt(NOW.plus(Duration.ofMinutes(15)));
        session.setCreatedAt(NOW);
        session.setVersion(1);
        return session;
    }

    private FileProperties fileProperties() {
        return new FileProperties(
                new FileProperties.Storage("http://127.0.0.1:9000", "ak", "sk", BUCKET),
                new FileProperties.Upload(
                        MAX_SIZE_BYTES,
                        List.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(15)),
                new FileProperties.DownloadGrant(
                        Duration.ofMinutes(5), Duration.ofMinutes(15), List.of("PROFILE_AVATAR", "PUBLIC_CATALOG")),
                new FileProperties.Cleanup(Duration.ofHours(24), Duration.ofMinutes(15), 50),
                new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                new FileProperties.Internal("bootstrap", List.of("user-service"), "educloud-file"),
                new FileProperties.Jwt("file:/jwks.json", "https://issuer.educloud.local", "educloud-api"));
    }
}
