package com.educloud.search.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.search.entity.IndexTaskEntity;
import com.educloud.search.entity.SearchInboxEntity;
import com.educloud.search.enums.TaskStatus;
import com.educloud.search.enums.TaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IndexTaskMapperTest {

    @Test
    @DisplayName("测试 TaskType 与 TaskStatus 枚举常量与转换")
    void testEnums() {
        assertThat(TaskType.valueOf("FULL_REBUILD")).isEqualTo(TaskType.FULL_REBUILD);
        assertThat(TaskType.valueOf("INCREMENTAL_REPAIR")).isEqualTo(TaskType.INCREMENTAL_REPAIR);
        assertThat(TaskType.values()).hasSize(2);

        assertThat(TaskStatus.valueOf("PENDING")).isEqualTo(TaskStatus.PENDING);
        assertThat(TaskStatus.valueOf("RUNNING")).isEqualTo(TaskStatus.RUNNING);
        assertThat(TaskStatus.valueOf("SUCCESS")).isEqualTo(TaskStatus.SUCCESS);
        assertThat(TaskStatus.valueOf("FAILED")).isEqualTo(TaskStatus.FAILED);
        assertThat(TaskStatus.values()).hasSize(4);
    }

    @Test
    @DisplayName("测试 IndexTaskEntity 字段与 MyBatis-Plus 注解")
    void testIndexTaskEntity() throws NoSuchFieldException {
        LocalDateTime now = LocalDateTime.now();
        IndexTaskEntity entity = IndexTaskEntity.builder()
                .id(10001L)
                .taskNo("TASK_20260826_001")
                .indexName("educloud_course_v1")
                .aliasName("educloud_course_search")
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.RUNNING)
                .totalRecords(500)
                .processedRecords(100)
                .failedRecords(2)
                .errorMessage("network glitch")
                .startedAt(now)
                .finishedAt(null)
                .createdBy("admin")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(entity.getId()).isEqualTo(10001L);
        assertThat(entity.getTaskNo()).isEqualTo("TASK_20260826_001");
        assertThat(entity.getIndexName()).isEqualTo("educloud_course_v1");
        assertThat(entity.getAliasName()).isEqualTo("educloud_course_search");
        assertThat(entity.getTaskType()).isEqualTo(TaskType.FULL_REBUILD);
        assertThat(entity.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(entity.getTotalRecords()).isEqualTo(500);
        assertThat(entity.getProcessedRecords()).isEqualTo(100);
        assertThat(entity.getFailedRecords()).isEqualTo(2);
        assertThat(entity.getErrorMessage()).isEqualTo("network glitch");
        assertThat(entity.getCreatedBy()).isEqualTo("admin");

        TableName tableName = IndexTaskEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("search_index_task");

        Field idField = IndexTaskEntity.class.getDeclaredField("id");
        TableId tableId = idField.getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.ASSIGN_ID);
    }

    @Test
    @DisplayName("测试 SearchInboxEntity 字段与 MyBatis-Plus 注解")
    void testSearchInboxEntity() throws NoSuchFieldException {
        LocalDateTime now = LocalDateTime.now();
        SearchInboxEntity inbox = SearchInboxEntity.builder()
                .id(20001L)
                .messageId("msg_99901")
                .eventType("CoursePublished")
                .aggregateType("Course")
                .aggregateId("3001")
                .aggregateVersion(3L)
                .payload("{\"courseId\":3001,\"title\":\"Java\"}")
                .status("PROCESSED")
                .errorReason(null)
                .createdAt(now)
                .build();

        assertThat(inbox.getId()).isEqualTo(20001L);
        assertThat(inbox.getMessageId()).isEqualTo("msg_99901");
        assertThat(inbox.getEventType()).isEqualTo("CoursePublished");
        assertThat(inbox.getAggregateType()).isEqualTo("Course");
        assertThat(inbox.getAggregateId()).isEqualTo("3001");
        assertThat(inbox.getAggregateVersion()).isEqualTo(3L);
        assertThat(inbox.getPayload()).contains("courseId");
        assertThat(inbox.getStatus()).isEqualTo("PROCESSED");

        TableName tableName = SearchInboxEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("search_sync_inbox");

        Field idField = SearchInboxEntity.class.getDeclaredField("id");
        TableId tableId = idField.getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.ASSIGN_ID);
    }

    @Test
    @DisplayName("测试 Mapper 接口继承 BaseMapper")
    void testMappersAreBaseMappers() {
        assertThat(BaseMapper.class).isAssignableFrom(IndexTaskMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(SearchInboxMapper.class);
    }
}
