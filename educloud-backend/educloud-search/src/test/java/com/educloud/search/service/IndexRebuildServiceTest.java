package com.educloud.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.dto.response.IndexTaskProgressResponse;
import com.educloud.search.entity.IndexTaskEntity;
import com.educloud.search.enums.TaskStatus;
import com.educloud.search.enums.TaskType;
import com.educloud.search.mapper.IndexTaskMapper;
import com.educloud.search.service.impl.IndexRebuildServiceImpl;
import com.educloud.search.support.CourseDataExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndexRebuildServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private IndexTaskMapper indexTaskMapper;

    @Mock
    private CourseDataExtractor courseDataExtractor;

    private ElasticsearchProperties properties;
    private IndexRebuildServiceImpl indexRebuildService;

    @BeforeEach
    void setUp() {
        properties = new ElasticsearchProperties();
        properties.setAliasName("educloud_course_search");
        properties.setIndexPrefix("educloud_course_v");
        indexRebuildService = new IndexRebuildServiceImpl(
                elasticsearchClient,
                properties,
                indexTaskMapper,
                courseDataExtractor
        );
    }

    @Test
    @DisplayName("测试 triggerFullRebuild 成功生成任务记录并返回初始进度")
    void testTriggerFullRebuild() {
        when(courseDataExtractor.countPublishedCourses()).thenReturn(250);

        IndexTaskProgressResponse response = indexRebuildService.triggerFullRebuild("admin_user");

        assertThat(response).isNotNull();
        assertThat(response.getTaskNo()).startsWith("SR_");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(response.getTaskType()).isEqualTo(TaskType.FULL_REBUILD);
        assertThat(response.getTotalRecords()).isEqualTo(250);
        assertThat(response.getProcessedRecords()).isEqualTo(0);
        assertThat(response.getCreatedBy()).isEqualTo("admin_user");

        verify(indexTaskMapper, atLeastOnce()).insert(any(IndexTaskEntity.class));
    }

    @Test
    @DisplayName("测试 executeFullRebuild 全流程成功：索引创建 -> Bulk写入 -> 原子别名切换 -> 状态更新为 SUCCESS")
    void testExecuteFullRebuildSuccess() throws IOException {
        String taskNo = "SR_20260826120000_TEST";
        String newIndexName = "educloud_course_v1724673600000";

        IndexTaskEntity initialTask = IndexTaskEntity.builder()
                .id(1L)
                .taskNo(taskNo)
                .indexName(newIndexName)
                .aliasName("educloud_course_search")
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.RUNNING)
                .totalRecords(2)
                .processedRecords(0)
                .failedRecords(0)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();

        when(indexTaskMapper.selectOne(any(QueryWrapper.class))).thenReturn(initialTask);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        // 1. Mock 创建新索引
        when(indicesClient.create(any(Function.class))).thenReturn(mock(CreateIndexResponse.class));

        // 2. Mock 分批读取与 Bulk 写入
        CourseIndexDoc doc1 = CourseIndexDoc.builder().id("101").courseId("101").title("Java 入门").build();
        CourseIndexDoc doc2 = CourseIndexDoc.builder().id("102").courseId("102").title("Spring Boot 实战").build();

        when(courseDataExtractor.extractPublishedCourses(0, 100)).thenReturn(List.of(doc1, doc2));
        when(courseDataExtractor.extractPublishedCourses(2, 100)).thenReturn(Collections.emptyList());

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        // 3. Mock 别名查询（旧索引为 educloud_course_v1）与切换
        GetAliasResponse getAliasResponse = mock(GetAliasResponse.class);
        Map<String, IndexAliases> aliasResult = new HashMap<>();
        aliasResult.put("educloud_course_v1", mock(IndexAliases.class));
        when(getAliasResponse.result()).thenReturn(aliasResult);
        when(indicesClient.getAlias(any(Function.class))).thenReturn(getAliasResponse);
        when(indicesClient.updateAliases(any(Function.class))).thenReturn(mock(UpdateAliasesResponse.class));

        // 执行
        indexRebuildService.executeFullRebuild(taskNo, newIndexName);

        // 验证各步骤
        verify(indicesClient, times(1)).create(any(Function.class));
        verify(elasticsearchClient, times(1)).bulk(any(BulkRequest.class));
        verify(indicesClient, times(1)).getAlias(any(Function.class));
        verify(indicesClient, times(1)).updateAliases(any(Function.class));

        // 验证最终状态更新为 SUCCESS
        ArgumentCaptor<IndexTaskEntity> captor = ArgumentCaptor.forClass(IndexTaskEntity.class);
        verify(indexTaskMapper, atLeastOnce()).updateById(captor.capture());
        IndexTaskEntity lastSaved = captor.getValue();
        assertThat(lastSaved.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(lastSaved.getProcessedRecords()).isEqualTo(2);
        assertThat(lastSaved.getFailedRecords()).isEqualTo(0);
        assertThat(lastSaved.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("测试 executeFullRebuild 异常回滚保护：写入失败时标记 FAILED，严格跳过别名切换并清理临时索引")
    void testExecuteFullRebuildFailureRollbackSafety() throws IOException {
        String taskNo = "SR_20260826120000_FAIL";
        String newIndexName = "educloud_course_v9999999999999";

        IndexTaskEntity initialTask = IndexTaskEntity.builder()
                .id(2L)
                .taskNo(taskNo)
                .indexName(newIndexName)
                .aliasName("educloud_course_search")
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.RUNNING)
                .totalRecords(10)
                .processedRecords(0)
                .failedRecords(0)
                .build();

        when(indexTaskMapper.selectOne(any(QueryWrapper.class))).thenReturn(initialTask);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        // Mock 创建新索引成功
        when(indicesClient.create(any(Function.class))).thenReturn(mock(CreateIndexResponse.class));

        // Mock 抽取过程或 Bulk 抛出异常
        when(courseDataExtractor.extractPublishedCourses(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Database connection timeout during extraction"));

        when(indicesClient.delete(any(Function.class))).thenReturn(mock(DeleteIndexResponse.class));

        // 执行
        indexRebuildService.executeFullRebuild(taskNo, newIndexName);

        // 验证绝不调用 updateAliases（别名不切换）
        verify(indicesClient, never()).updateAliases(any(Function.class));

        // 验证清理临时物理索引
        verify(indicesClient, times(1)).delete(any(Function.class));

        // 验证更新数据库状态为 FAILED
        ArgumentCaptor<IndexTaskEntity> captor = ArgumentCaptor.forClass(IndexTaskEntity.class);
        verify(indexTaskMapper, atLeastOnce()).updateById(captor.capture());
        IndexTaskEntity lastSaved = captor.getValue();
        assertThat(lastSaved.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(lastSaved.getErrorMessage()).contains("Database connection timeout during extraction");
        assertThat(lastSaved.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("测试 getTaskProgress 能够正确返回进度详情及百分比")
    void testGetTaskProgress() {
        String taskNo = "SR_20260826123000_ABCD";
        IndexTaskEntity entity = IndexTaskEntity.builder()
                .taskNo(taskNo)
                .indexName("educloud_course_v100")
                .aliasName("educloud_course_search")
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.RUNNING)
                .totalRecords(100)
                .processedRecords(65)
                .failedRecords(0)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();

        when(indexTaskMapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

        IndexTaskProgressResponse response = indexRebuildService.getTaskProgress(taskNo);

        assertThat(response).isNotNull();
        assertThat(response.getTaskNo()).isEqualTo(taskNo);
        assertThat(response.getProgressPercent()).isEqualTo(65);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    @DisplayName("测试 getTaskProgress 当任务不存在或传入空值时返回 null")
    void testGetTaskProgressNotFound() {
        IndexTaskProgressResponse response1 = indexRebuildService.getTaskProgress(null);
        assertThat(response1).isNull();

        when(indexTaskMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        IndexTaskProgressResponse response2 = indexRebuildService.getTaskProgress("NON_EXIST");
        assertThat(response2).isNull();
    }

    @Test
    @DisplayName("测试 listRecentTasks 查询最近任务列表")
    void testListRecentTasks() {
        IndexTaskEntity task1 = IndexTaskEntity.builder().taskNo("SR_001").status(TaskStatus.SUCCESS).totalRecords(50).processedRecords(50).build();
        IndexTaskEntity task2 = IndexTaskEntity.builder().taskNo("SR_002").status(TaskStatus.FAILED).totalRecords(100).processedRecords(20).build();

        when(indexTaskMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(task1, task2));

        List<IndexTaskProgressResponse> list = indexRebuildService.listRecentTasks(10);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getTaskNo()).isEqualTo("SR_001");
        assertThat(list.get(0).getProgressPercent()).isEqualTo(100);
        assertThat(list.get(1).getTaskNo()).isEqualTo("SR_002");
        assertThat(list.get(1).getProgressPercent()).isEqualTo(20);
    }
}
