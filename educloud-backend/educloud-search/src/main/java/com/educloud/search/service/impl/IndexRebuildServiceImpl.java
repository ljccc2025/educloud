package com.educloud.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.dto.response.IndexTaskProgressResponse;
import com.educloud.search.entity.IndexTaskEntity;
import com.educloud.search.enums.TaskStatus;
import com.educloud.search.enums.TaskType;
import com.educloud.search.mapper.IndexTaskMapper;
import com.educloud.search.service.IndexRebuildService;
import com.educloud.search.support.CourseDataExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 管理端全量索引平滑重建服务实现类
 * 支持分批抽取写入、ES Bulk 批处理、零停机别名原子切换与异常回滚保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexRebuildServiceImpl implements IndexRebuildService {

    private static final String SCHEMA_PATH = "elasticsearch/course-v1.json";
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;
    private final IndexTaskMapper indexTaskMapper;
    private final CourseDataExtractor courseDataExtractor;

    @Override
    public IndexTaskProgressResponse triggerFullRebuild(String operator) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        String taskNo = "SR_" + timestamp + "_" + randomSuffix;

        String prefix = properties.getIndexPrefix() != null ? properties.getIndexPrefix() : "educloud_course_v";
        String newIndexName = prefix + System.currentTimeMillis();

        int totalRecords = courseDataExtractor.countPublishedCourses();
        LocalDateTime now = LocalDateTime.now();

        IndexTaskEntity entity = IndexTaskEntity.builder()
                .taskNo(taskNo)
                .indexName(newIndexName)
                .aliasName(properties.getAliasName())
                .taskType(TaskType.FULL_REBUILD)
                .status(TaskStatus.RUNNING)
                .totalRecords(totalRecords)
                .processedRecords(0)
                .failedRecords(0)
                .startedAt(now)
                .createdBy(StringUtils.hasText(operator) ? operator.trim() : "SYSTEM")
                .createdAt(now)
                .updatedAt(now)
                .build();

        indexTaskMapper.insert(entity);
        log.info("Triggered full index rebuild task [{}] targeting new index [{}], total records: {}",
                taskNo, newIndexName, totalRecords);

        // 启动异步线程执行全量重建
        CompletableFuture.runAsync(() -> {
            try {
                executeFullRebuild(taskNo, newIndexName);
            } catch (Exception e) {
                log.error("Async full rebuild execution failed for task [{}]: {}", taskNo, e.getMessage(), e);
            }
        });

        return IndexTaskProgressResponse.fromEntity(entity);
    }

    @Override
    public void executeFullRebuild(String taskNo, String newIndexName) {
        IndexTaskEntity task = indexTaskMapper.selectOne(
                new QueryWrapper<IndexTaskEntity>().eq("task_no", taskNo));
        if (task == null) {
            log.error("Task [{}] not found in database. Aborting rebuild.", taskNo);
            return;
        }

        try {
            // 1. 基于 course-v1.json 创建全新的物理索引
            log.info("Task [{}]: Creating new physical index [{}] from schema [{}]...", taskNo, newIndexName, SCHEMA_PATH);
            try (InputStream is = openSchemaStream()) {
                elasticsearchClient.indices().create(c -> c.index(newIndexName).withJson(is));
            }
            log.info("Task [{}]: Physical index [{}] created successfully.", taskNo, newIndexName);

            // 2. 分批抽取并 Bulk 写入新索引
            int offset = 0;
            int totalProcessed = 0;
            int failedCount = 0;

            while (true) {
                List<CourseIndexDoc> batchDocs = courseDataExtractor.extractPublishedCourses(offset, DEFAULT_BATCH_SIZE);
                if (batchDocs == null || batchDocs.isEmpty()) {
                    break;
                }

                BulkRequest.Builder br = new BulkRequest.Builder();
                for (CourseIndexDoc doc : batchDocs) {
                    br.operations(op -> op.index(idx -> idx.index(newIndexName).id(doc.getId()).document(doc)));
                }

                BulkResponse bulkResponse = elasticsearchClient.bulk(br.build());
                if (bulkResponse != null && bulkResponse.errors()) {
                    log.warn("Bulk indexing encountered errors for task [{}]: {}", taskNo, bulkResponse.items());
                    for (BulkResponseItem item : bulkResponse.items()) {
                        if (item.error() != null) {
                            failedCount++;
                        }
                    }
                }

                totalProcessed += batchDocs.size();
                offset += batchDocs.size();

                // 实时更新任务进度
                task.setProcessedRecords(totalProcessed);
                task.setFailedRecords(failedCount);
                task.setUpdatedAt(LocalDateTime.now());
                indexTaskMapper.updateById(task);

                log.info("Task [{}]: Processed {}/{} records into [{}]",
                        taskNo, totalProcessed, task.getTotalRecords(), newIndexName);
            }

            // 3. 零停机别名原子切换 (Atomic Alias Swap)
            String aliasName = properties.getAliasName();
            log.info("Task [{}]: Performing zero-downtime atomic alias swap for alias [{}] -> [{}]...",
                    taskNo, aliasName, newIndexName);

            List<String> oldIndices = new ArrayList<>();
            try {
                GetAliasResponse getAliasResponse = elasticsearchClient.indices().getAlias(a -> a.name(aliasName));
                if (getAliasResponse != null && getAliasResponse.result() != null) {
                    oldIndices.addAll(getAliasResponse.result().keySet());
                }
            } catch (Exception e) {
                log.info("Task [{}]: No existing alias [{}] or query failed: {}. Will add alias directly.",
                        taskNo, aliasName, e.getMessage());
            }

            elasticsearchClient.indices().updateAliases(u -> {
                for (String oldIdx : oldIndices) {
                    if (!oldIdx.equals(newIndexName)) {
                        u.actions(act -> act.remove(r -> r.index(oldIdx).alias(aliasName)));
                    }
                }
                u.actions(act -> act.add(a -> a.index(newIndexName).alias(aliasName)));
                return u;
            });
            log.info("Task [{}]: Successfully completed atomic alias swap. Alias [{}] now points to [{}] (removed from: {}).",
                    taskNo, aliasName, newIndexName, oldIndices);

            // 4. 标记任务成功
            task.setStatus(TaskStatus.SUCCESS);
            task.setFinishedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            indexTaskMapper.updateById(task);
            log.info("Task [{}]: Full rebuild completed successfully.", taskNo);

        } catch (Exception e) {
            log.error("Task [{}]: Full index rebuild failed: {}. Alias swap is strictly SKIPPED.", taskNo, e.getMessage(), e);

            try {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                indexTaskMapper.updateById(task);
            } catch (Exception dbEx) {
                log.error("Task [{}]: Failed to record FAILED status to DB: {}", taskNo, dbEx.getMessage());
            }

            // 清理创建的新物理索引（如果已创建），防止未完成脏索引残留
            try {
                elasticsearchClient.indices().delete(d -> d.index(newIndexName));
                log.info("Task [{}]: Cleaned up unaliased physical index [{}] after failure.", taskNo, newIndexName);
            } catch (Exception delEx) {
                log.warn("Task [{}]: Could not clean up temporary index [{}]: {}", taskNo, newIndexName, delEx.getMessage());
            }
        }
    }

    @Override
    public IndexTaskProgressResponse getTaskProgress(String taskNo) {
        if (!StringUtils.hasText(taskNo)) {
            return null;
        }
        IndexTaskEntity task = indexTaskMapper.selectOne(
                new QueryWrapper<IndexTaskEntity>().eq("task_no", taskNo.trim()));
        return IndexTaskProgressResponse.fromEntity(task);
    }

    @Override
    public List<IndexTaskProgressResponse> listRecentTasks(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 100);
        List<IndexTaskEntity> list = indexTaskMapper.selectList(
                new QueryWrapper<IndexTaskEntity>().orderByDesc("created_at").last("LIMIT " + safeLimit));
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(IndexTaskProgressResponse::fromEntity).toList();
    }

    private InputStream openSchemaStream() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(SCHEMA_PATH);
        if (is == null) {
            is = getClass().getResourceAsStream("/" + SCHEMA_PATH);
        }
        if (is == null) {
            ClassPathResource resource = new ClassPathResource(SCHEMA_PATH);
            is = resource.getInputStream();
        }
        if (is == null) {
            throw new IllegalStateException("Elasticsearch schema resource not found at " + SCHEMA_PATH);
        }
        return is;
    }
}
