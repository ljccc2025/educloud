package com.educloud.file.service;

import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.FileBoundException;
import com.educloud.file.exception.VersionConflictException;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.storage.StorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 5：文件对象服务单元测试（mock UploadSessionService/Mapper/StorageGateway）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 5 —— completeUpload 委托
 * UploadSessionService.complete（事件任务 11 接）；deleteIfUnbound 先锁根并查活跃绑定，
 * 有则 FileBoundException，否则删 MinIO 对象 + 行置 DELETED + deleted_at；
 * 文件不存在按幂等返回（本实现选择幂等语义，任务要求二选一）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileObjectServiceTest {

    private static final long UPLOADER_ID = 42L;
    private static final long SESSION_ID = 77L;
    private static final long FILE_ID = 1001L;
    private static final String BUCKET = "educloud-files";
    private static final String OBJECT_KEY = "educloud-files/user-42/20260822/abc.png";
    private static final String REASON = "manual-cleanup";
    private static final Instant NOW = Instant.parse("2026-08-22T11:30:00Z");

    @Mock
    private UploadSessionService uploadSessionService;
    @Mock
    private FileObjectMapper objectMapper;
    @Mock
    private FileBindingMapper bindingMapper;
    @Mock
    private StorageGateway storageGateway;

    private FileObjectService service;

    @BeforeEach
    void setUp() {
        service = new FileObjectService(
                uploadSessionService, objectMapper, bindingMapper, storageGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completeUploadDelegatesToUploadSessionService() {
        FileObjectEntity expected = availableFile();
        when(uploadSessionService.complete(UPLOADER_ID, SESSION_ID)).thenReturn(expected);

        FileObjectEntity result = service.completeUpload(UPLOADER_ID, SESSION_ID);

        assertThat(result).isSameAs(expected);
        verify(uploadSessionService).complete(UPLOADER_ID, SESSION_ID);
    }

    @Test
    void deleteIfUnboundRejectsWhenActiveBindingExists() {
        FileObjectEntity root = availableFile();
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);
        when(bindingMapper.countActiveByFileId(FILE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteIfUnbound(FILE_ID, REASON))
                .isInstanceOf(FileBoundException.class);

        verify(storageGateway, never()).deleteObject(anyString(), anyString());
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
    }

    @Test
    void deleteIfUnboundDeletesObjectAndMarksRowDeleted() {
        FileObjectEntity root = availableFile();
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);
        when(bindingMapper.countActiveByFileId(FILE_ID)).thenReturn(0L);
        when(objectMapper.updateById(any(FileObjectEntity.class))).thenReturn(1);

        service.deleteIfUnbound(FILE_ID, REASON);

        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY);

        ArgumentCaptor<FileObjectEntity> captor =
                ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(objectMapper).updateById(captor.capture());
        FileObjectEntity updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("DELETED");
        assertThat(updated.getDeletedAt()).isEqualTo(NOW);
        // mock 返回 1 表示 DB 命中：传给 updateById 的实体 version 必须仍是旧值，拦截器才能
        // 生成 WHERE version=旧 并 SET 旧+1；成功后的版本写回属框架行为，单测不模拟。
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void deleteIfUnboundThrowsVersionConflictWhenRootUpdateMisses() {
        FileObjectEntity root = availableFile();
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);
        when(bindingMapper.countActiveByFileId(FILE_ID)).thenReturn(0L);
        // 拦截器按旧 version 生成 WHERE 条件，0 行命中即版本冲突（不 mock 则默认返回 0）。
        when(objectMapper.updateById(any(FileObjectEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service.deleteIfUnbound(FILE_ID, REASON))
                .isInstanceOf(VersionConflictException.class);

        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY);
        verify(objectMapper).updateById(root);
        // 冲突时版本保持读出的旧值，不得在内存中自增。
        assertThat(root.getVersion()).isEqualTo(1);
    }

    @Test
    void deleteIfUnboundIsIdempotentWhenFileMissing() {
        // 文件不存在按幂等返回（不抛、不触碰存储/绑定表），注释见 FileObjectService.deleteIfUnbound。
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(null);

        service.deleteIfUnbound(FILE_ID, REASON);

        verify(bindingMapper, never()).countActiveByFileId(anyLong());
        verify(storageGateway, never()).deleteObject(anyString(), anyString());
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
    }

    private FileObjectEntity availableFile() {
        FileObjectEntity root = new FileObjectEntity();
        root.setId(FILE_ID);
        root.setObjectKey(OBJECT_KEY);
        root.setBucket(BUCKET);
        root.setStatus("AVAILABLE");
        root.setVersion(1);
        return root;
    }
}
