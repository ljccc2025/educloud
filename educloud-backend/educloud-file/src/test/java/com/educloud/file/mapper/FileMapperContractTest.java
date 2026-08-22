package com.educloud.file.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileAccessAuditEntity;
import com.educloud.file.entity.FileBindingEntity;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.entity.FileUploadSessionEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 2 的 Mapper 契约测试（纯 Mockito，无需 Spring 上下文与数据库）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 2 —— 4 个 Mapper 接口存在且继承
 * BaseMapper；insert/selectOne 冒烟验证 Mapper 可被 mock 使用；实体注解契约与
 * V001__file.sql 表名、@Version 乐观锁归属保持一致（真实 CRUD 由任务 1 FileSchemaIT 覆盖）。</p>
 */
class FileMapperContractTest {

    @Test
    void allFileMappersExistAndExtendBaseMapper() {
        assertThat(BaseMapper.class).isAssignableFrom(FileUploadSessionMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(FileObjectMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(FileBindingMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(FileAccessAuditMapper.class);
    }

    @Test
    void entitiesMapToV001TableNames() {
        assertThat(FileUploadSessionEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("file_upload_session");
        assertThat(FileObjectEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("file_object");
        assertThat(FileBindingEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("file_binding");
        assertThat(FileAccessAuditEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("file_access_audit");
    }

    @Test
    void onlyFileObjectEntityCarriesOptimisticLockVersion() throws Exception {
        Field fileObjectVersion = FileObjectEntity.class.getDeclaredField("version");
        assertThat(fileObjectVersion.getAnnotation(Version.class))
                .as("file_object.version 是绑定/删除事务的乐观锁版本")
                .isNotNull();

        // file_upload_session.version 是轮换计数普通字段：字段存在但无 @Version。
        assertThat(FileUploadSessionEntity.class.getDeclaredField("version")
                .getAnnotation(Version.class)).isNull();

        // file_binding / file_access_audit 在 V001 中没有 version 列：断言字段不存在。
        assertThat(hasField(FileBindingEntity.class, "version"))
                .as("file_binding 无 version 列")
                .isFalse();
        assertThat(hasField(FileAccessAuditEntity.class, "version"))
                .as("file_access_audit 无 version 列")
                .isFalse();
    }

    private static boolean hasField(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getName().equals(name));
    }

    @Test
    void uploadSessionMapperInsertAndSelectOneContract() {
        FileUploadSessionMapper mapper = mock(FileUploadSessionMapper.class);
        FileUploadSessionEntity entity = new FileUploadSessionEntity();

        when(mapper.insert(entity)).thenReturn(1);
        assertThat(mapper.insert(entity)).isEqualTo(1);
        verify(mapper).insert(entity);

        when(mapper.selectOne(any())).thenReturn(entity);
        assertThat(mapper.selectOne(any())).isSameAs(entity);
        verify(mapper).selectOne(any());
    }

    @Test
    void fileObjectMapperInsertAndSelectOneContract() {
        FileObjectMapper mapper = mock(FileObjectMapper.class);
        FileObjectEntity entity = new FileObjectEntity();

        when(mapper.insert(entity)).thenReturn(1);
        assertThat(mapper.insert(entity)).isEqualTo(1);
        verify(mapper).insert(entity);

        when(mapper.selectOne(any())).thenReturn(entity);
        assertThat(mapper.selectOne(any())).isSameAs(entity);
        verify(mapper).selectOne(any());
    }

    @Test
    void fileBindingMapperInsertAndSelectOneContract() {
        FileBindingMapper mapper = mock(FileBindingMapper.class);
        FileBindingEntity entity = new FileBindingEntity();

        when(mapper.insert(entity)).thenReturn(1);
        assertThat(mapper.insert(entity)).isEqualTo(1);
        verify(mapper).insert(entity);

        when(mapper.selectOne(any())).thenReturn(entity);
        assertThat(mapper.selectOne(any())).isSameAs(entity);
        verify(mapper).selectOne(any());
    }

    @Test
    void fileAccessAuditMapperInsertAndSelectOneContract() {
        FileAccessAuditMapper mapper = mock(FileAccessAuditMapper.class);
        FileAccessAuditEntity entity = new FileAccessAuditEntity();

        when(mapper.insert(entity)).thenReturn(1);
        assertThat(mapper.insert(entity)).isEqualTo(1);
        verify(mapper).insert(entity);

        when(mapper.selectOne(any())).thenReturn(entity);
        assertThat(mapper.selectOne(any())).isSameAs(entity);
        verify(mapper).selectOne(any());
    }
}
