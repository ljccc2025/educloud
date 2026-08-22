package com.educloud.file.service;

import com.educloud.file.entity.FileBindingEntity;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.FileNotAvailableException;
import com.educloud.file.exception.FileNotFoundException;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 5：业务绑定服务单元测试（mock Mapper + Clock，不依赖真实 DB）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 5 —— bind/unbind 均先
 * SELECT ... FOR UPDATE 锁 file_object 根；bind 写 file_binding 并根 version+1；
 * 重复 bind（同 owner）幂等不重复插入；文件不存在/非 AVAILABLE 拒绝；
 * unbind 置 unbound_at 且根 version+1；未绑定 unbind 幂等。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileBindingServiceTest {

    private static final long FILE_ID = 1001L;
    private static final String OWNER_SERVICE = "educloud-user";
    private static final String OWNER_TYPE = "USER_PROFILE";
    private static final String OWNER_ID = "u-42";
    private static final Instant NOW = Instant.parse("2026-08-22T11:00:00Z");

    @Mock
    private FileObjectMapper objectMapper;
    @Mock
    private FileBindingMapper bindingMapper;

    private FileBindingService service;

    @BeforeEach
    void setUp() {
        service = new FileBindingService(
                objectMapper, bindingMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void bindPersistsBindingAndIncrementsRootVersion() {
        FileObjectEntity root = availableFile();
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(null);

        service.bind(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID);

        ArgumentCaptor<FileBindingEntity> insertCaptor =
                ArgumentCaptor.forClass(FileBindingEntity.class);
        verify(bindingMapper).insert(insertCaptor.capture());
        FileBindingEntity inserted = insertCaptor.getValue();
        assertThat(inserted.getFileId()).isEqualTo(FILE_ID);
        assertThat(inserted.getOwnerService()).isEqualTo(OWNER_SERVICE);
        assertThat(inserted.getOwnerType()).isEqualTo(OWNER_TYPE);
        assertThat(inserted.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(inserted.getBoundAt()).isEqualTo(NOW);
        assertThat(inserted.getUnboundAt()).isNull();

        ArgumentCaptor<FileObjectEntity> updateCaptor =
                ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(objectMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getVersion()).isEqualTo(2);
    }

    @Test
    void bindRepeatedForSameOwnerIsIdempotent() {
        FileObjectEntity root = availableFile();
        FileBindingEntity active = new FileBindingEntity();
        active.setId(9L);
        active.setUnboundAt(null);
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(active);

        service.bind(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID);

        verify(bindingMapper, never()).insert(any(FileBindingEntity.class));
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
    }

    @Test
    void bindRejectsWhenFileNotFound() {
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.bind(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .isInstanceOf(FileNotFoundException.class);

        verify(bindingMapper, never()).insert(any(FileBindingEntity.class));
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
    }

    @Test
    void bindRejectsWhenFileNotAvailable() {
        FileObjectEntity root = availableFile();
        root.setStatus("UPLOADING");
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);

        assertThatThrownBy(() -> service.bind(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .isInstanceOf(FileNotAvailableException.class);

        verify(bindingMapper, never()).insert(any(FileBindingEntity.class));
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
    }

    @Test
    void unbindSetsUnboundAtAndIncrementsRootVersion() {
        FileObjectEntity root = availableFile();
        FileBindingEntity active = new FileBindingEntity();
        active.setId(9L);
        active.setFileId(FILE_ID);
        active.setOwnerService(OWNER_SERVICE);
        active.setOwnerType(OWNER_TYPE);
        active.setOwnerId(OWNER_ID);
        active.setBoundAt(NOW);
        active.setUnboundAt(null);
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(active);

        service.unbind(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID);

        ArgumentCaptor<FileBindingEntity> bindingCaptor =
                ArgumentCaptor.forClass(FileBindingEntity.class);
        verify(bindingMapper).updateById(bindingCaptor.capture());
        assertThat(bindingCaptor.getValue().getUnboundAt()).isEqualTo(NOW);

        ArgumentCaptor<FileObjectEntity> objectCaptor =
                ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(objectMapper).updateById(objectCaptor.capture());
        assertThat(objectCaptor.getValue().getVersion()).isEqualTo(2);
    }

    @Test
    void unbindWhenNotBoundIsIdempotent() {
        FileObjectEntity root = availableFile();
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(root);
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(null);

        service.unbind(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID);

        verify(bindingMapper, never()).updateById(any(FileBindingEntity.class));
        verify(objectMapper, never()).updateById(any(FileObjectEntity.class));
    }

    private FileObjectEntity availableFile() {
        FileObjectEntity root = new FileObjectEntity();
        root.setId(FILE_ID);
        root.setObjectKey("educloud-files/user-42/20260822/abc.png");
        root.setBucket("educloud-files");
        root.setStatus("AVAILABLE");
        root.setVersion(1);
        return root;
    }
}
