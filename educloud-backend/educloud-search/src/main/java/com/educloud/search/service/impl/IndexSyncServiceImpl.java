package com.educloud.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.document.LessonDoc;
import com.educloud.search.entity.SearchInboxEntity;
import com.educloud.search.mapper.SearchInboxMapper;
import com.educloud.search.messaging.event.ContentDomainEvent;
import com.educloud.search.messaging.event.CourseDomainEvent;
import com.educloud.search.service.IndexSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 实时增量索引同步服务实现类
 * 具备消费幂等性校验、基于 aggregateVersion 的防乱序保证与级联课件维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSyncServiceImpl implements IndexSyncService {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;
    private final SearchInboxMapper searchInboxMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void handleCourseEvent(CourseDomainEvent event) {
        if (event == null) {
            return;
        }

        String messageId = event.getEffectiveMessageId();
        String eventType = event.getEventType() != null ? event.getEventType() : "CourseEvent";
        String aggregateType = event.getAggregateType() != null ? event.getAggregateType() : "Course";
        Long eventVersion = event.getAggregateVersion();

        // 1. 幂等性校验 (查询 search_sync_inbox)
        if (isAlreadyProcessed(messageId)) {
            log.info("Course message [{}] already processed. Skipping for idempotency.", messageId);
            return;
        }

        // 解析 courseId
        String courseId = resolveCourseId(event);
        if (!StringUtils.hasText(courseId)) {
            log.warn("Course event [{}] missing courseId. Aborting sync.", messageId);
            recordInbox(messageId, eventType, aggregateType, event.getAggregateId(), eventVersion, serializePayload(event), "IGNORED", "Missing courseId");
            return;
        }

        String aliasName = properties.getAliasName();
        CourseIndexDoc existingDoc = getCourseDocFromEs(aliasName, courseId);

        // 2. 版本防乱序校验
        if (existingDoc != null && existingDoc.getAggregateVersion() != null && eventVersion != null) {
            if (eventVersion <= existingDoc.getAggregateVersion()) {
                log.warn("Stale course event [{}] detected: event version [{}] <= doc version [{}]. Skipping.",
                        messageId, eventVersion, existingDoc.getAggregateVersion());
                recordInbox(messageId, eventType, aggregateType, courseId, eventVersion, serializePayload(event), "IGNORED",
                        "Stale version: " + eventVersion + " <= " + existingDoc.getAggregateVersion());
                return;
            }
        }

        // 3. 判断生命周期状态与事件类型
        CourseDomainEvent.CourseEventData data = event.getData();
        String status = data != null && data.getLifecycleStatus() != null ? data.getLifecycleStatus() : "";

        boolean isOfflineOrDeleted = isOfflineOrDeletedEvent(eventType, status);

        if (isOfflineOrDeleted) {
            // 下架或删除：从 ES 中物理删除文档
            try {
                elasticsearchClient.delete(d -> d.index(aliasName).id(courseId));
                log.info("Successfully deleted/offlined course document [{}] from ES alias [{}]", courseId, aliasName);
                recordInbox(messageId, eventType, aggregateType, courseId, eventVersion, serializePayload(event), "PROCESSED", null);
            } catch (Exception e) {
                log.warn("Exception while deleting course document [{}] from ES: {}", courseId, e.getMessage());
                recordInbox(messageId, eventType, aggregateType, courseId, eventVersion, serializePayload(event), "PROCESSED", null);
            }
        } else {
            // 发布或更新：组装/更新文档并写入 ES
            CourseIndexDoc docToSave = existingDoc != null ? existingDoc : CourseIndexDoc.builder().id(courseId).courseId(courseId).build();

            if (data != null) {
                if (data.getTitle() != null) docToSave.setTitle(data.getTitle());
                if (data.getSubtitle() != null) docToSave.setSubtitle(data.getSubtitle());
                if (data.getDescription() != null) docToSave.setDescription(data.getDescription());
                if (data.getTeacherId() != null) docToSave.setTeacherId(String.valueOf(data.getTeacherId()));
                if (data.getTeacherName() != null) docToSave.setTeacherName(data.getTeacherName());
                if (data.getCategory() != null) docToSave.setCategory(data.getCategory());
                if (data.getCategoryCode() != null) docToSave.setCategoryCode(data.getCategoryCode());
                if (data.getCoverUrl() != null) docToSave.setCoverUrl(data.getCoverUrl());
                if (data.getDifficulty() != null) docToSave.setDifficulty(data.getDifficulty());
                if (data.getPriceCents() != null) docToSave.setPriceCents(data.getPriceCents());
                if (data.getIsFree() != null) docToSave.setIsFree(data.getIsFree());
                if (data.getRating() != null) docToSave.setRating(data.getRating());
                if (data.getStudentCount() != null) docToSave.setStudentCount(data.getStudentCount());
                if (data.getLessonCount() != null) docToSave.setLessonCount(data.getLessonCount());
                if (data.getTags() != null) docToSave.setTags(data.getTags());
                if (data.getLessons() != null) docToSave.setLessons(data.getLessons());
                if (data.getPublishedAt() != null) docToSave.setPublishedAt(data.getPublishedAt());
            }

            docToSave.setStatus("PUBLISHED");
            docToSave.setAggregateVersion(eventVersion != null ? eventVersion : (docToSave.getAggregateVersion() != null ? docToSave.getAggregateVersion() : 1L));
            docToSave.setUpdatedAt(data != null && data.getUpdatedAt() != null ? data.getUpdatedAt() : LocalDateTime.now());
            if (docToSave.getPublishedAt() == null) {
                docToSave.setPublishedAt(LocalDateTime.now());
            }

            try {
                elasticsearchClient.index(i -> i.index(aliasName).id(courseId).document(docToSave));
                log.info("Successfully indexed course [{}] into ES alias [{}] with version [{}]", courseId, aliasName, docToSave.getAggregateVersion());
                recordInbox(messageId, eventType, aggregateType, courseId, eventVersion, serializePayload(event), "PROCESSED", null);
            } catch (Exception e) {
                log.error("Failed to index course [{}] into ES: {}", courseId, e.getMessage(), e);
                recordInbox(messageId, eventType, aggregateType, courseId, eventVersion, serializePayload(event), "FAILED", e.getMessage());
                throw new RuntimeException("Elasticsearch index failed for course " + courseId, e);
            }
        }
    }

    @Override
    public void handleContentEvent(ContentDomainEvent event) {
        if (event == null) {
            return;
        }

        String messageId = event.getEffectiveMessageId();
        String eventType = event.getEventType() != null ? event.getEventType() : "ContentEvent";
        String aggregateType = event.getAggregateType() != null ? event.getAggregateType() : "CourseContent";
        Long eventVersion = event.getAggregateVersion();

        // 1. 幂等性校验
        if (isAlreadyProcessed(messageId)) {
            log.info("Content message [{}] already processed. Skipping for idempotency.", messageId);
            return;
        }

        // 解析 courseId
        String courseId = resolveContentCourseId(event);
        if (!StringUtils.hasText(courseId)) {
            log.warn("Content event [{}] missing courseId. Skipping sync.", messageId);
            recordInbox(messageId, eventType, aggregateType, event.getAggregateId(), eventVersion, serializePayload(event), "IGNORED", "Missing courseId");
            return;
        }

        String aliasName = properties.getAliasName();
        CourseIndexDoc existingDoc = getCourseDocFromEs(aliasName, courseId);

        if (existingDoc == null) {
            log.info("Course document [{}] not found in ES for content sync. Recording IGNORED.", courseId);
            recordInbox(messageId, eventType, aggregateType, event.getAggregateId(), eventVersion, serializePayload(event), "IGNORED", "Course document not found in search index");
            return;
        }

        // 2. 版本防乱序校验
        if (existingDoc.getAggregateVersion() != null && eventVersion != null) {
            if (eventVersion <= existingDoc.getAggregateVersion()) {
                log.warn("Stale content event [{}] detected: event version [{}] <= doc version [{}]. Skipping.",
                        messageId, eventVersion, existingDoc.getAggregateVersion());
                recordInbox(messageId, eventType, aggregateType, event.getAggregateId(), eventVersion, serializePayload(event), "IGNORED",
                        "Stale version: " + eventVersion + " <= " + existingDoc.getAggregateVersion());
                return;
            }
        }

        // 3. 局部更新 lessons 列表与 lessonCount
        List<LessonDoc> lessons = existingDoc.getLessons() != null ? new ArrayList<>(existingDoc.getLessons()) : new ArrayList<>();
        ContentDomainEvent.ContentEventData data = event.getData();

        if ("ContentRevisionPublished".equalsIgnoreCase(eventType)) {
            if (data != null && data.getLessons() != null) {
                lessons = new ArrayList<>(data.getLessons());
            }
        } else if ("LessonDeleted".equalsIgnoreCase(eventType) || (data != null && "DELETE".equalsIgnoreCase(data.getAction()))) {
            if (data != null && data.getLessonId() != null) {
                String lessonIdStr = String.valueOf(data.getLessonId());
                lessons.removeIf(l -> lessonIdStr.equals(l.getId()));
            }
        } else if ("LessonPublished".equalsIgnoreCase(eventType) || "LessonUpdated".equalsIgnoreCase(eventType)
                || (data != null && ("ADD".equalsIgnoreCase(data.getAction()) || "UPDATE".equalsIgnoreCase(data.getAction())))) {
            if (data != null && data.getLessonId() != null) {
                String lessonIdStr = String.valueOf(data.getLessonId());
                LessonDoc target = null;
                for (LessonDoc l : lessons) {
                    if (lessonIdStr.equals(l.getId())) {
                        target = l;
                        break;
                    }
                }
                if (target == null) {
                    target = LessonDoc.builder()
                            .id(lessonIdStr)
                            .title(data.getTitle())
                            .chapterTitle(data.getChapterTitle())
                            .isPreview(data.getIsPreview() != null ? data.getIsPreview() : false)
                            .build();
                    lessons.add(target);
                } else {
                    if (data.getTitle() != null) target.setTitle(data.getTitle());
                    if (data.getChapterTitle() != null) target.setChapterTitle(data.getChapterTitle());
                    if (data.getIsPreview() != null) target.setIsPreview(data.getIsPreview());
                }
            }
        }

        existingDoc.setLessons(lessons);
        existingDoc.setLessonCount(lessons.size());
        if (eventVersion != null) {
            existingDoc.setAggregateVersion(eventVersion);
        }
        existingDoc.setUpdatedAt(LocalDateTime.now());

        // 4. 写回 ES 索引
        try {
            elasticsearchClient.index(i -> i.index(aliasName).id(courseId).document(existingDoc));
            log.info("Successfully updated lessons for course [{}] (count={}) in ES alias [{}]", courseId, lessons.size(), aliasName);
            recordInbox(messageId, eventType, aggregateType, event.getAggregateId(), eventVersion, serializePayload(event), "PROCESSED", null);
        } catch (Exception e) {
            log.error("Failed to update lessons for course [{}] in ES: {}", courseId, e.getMessage(), e);
            recordInbox(messageId, eventType, aggregateType, event.getAggregateId(), eventVersion, serializePayload(event), "FAILED", e.getMessage());
            throw new RuntimeException("Elasticsearch lesson update failed for course " + courseId, e);
        }
    }

    private boolean isAlreadyProcessed(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        try {
            SearchInboxEntity inbox = searchInboxMapper.selectOne(
                    new QueryWrapper<SearchInboxEntity>().eq("message_id", messageId));
            if (inbox != null) {
                String status = inbox.getStatus();
                return "PROCESSED".equalsIgnoreCase(status) || "IGNORED".equalsIgnoreCase(status);
            }
        } catch (Exception e) {
            log.warn("Failed to check inbox for messageId [{}]: {}", messageId, e.getMessage());
        }
        return false;
    }

    private void recordInbox(String messageId, String eventType, String aggregateType, String aggregateId,
                             Long aggregateVersion, String payload, String status, String errorReason) {
        if (!StringUtils.hasText(messageId)) {
            return;
        }
        try {
            SearchInboxEntity existing = searchInboxMapper.selectOne(
                    new QueryWrapper<SearchInboxEntity>().eq("message_id", messageId));
            if (existing != null) {
                existing.setStatus(status);
                existing.setErrorReason(errorReason);
                searchInboxMapper.updateById(existing);
            } else {
                SearchInboxEntity entity = SearchInboxEntity.builder()
                        .messageId(messageId)
                        .eventType(eventType)
                        .aggregateType(aggregateType)
                        .aggregateId(aggregateId)
                        .aggregateVersion(aggregateVersion)
                        .payload(payload)
                        .status(status)
                        .errorReason(errorReason)
                        .createdAt(LocalDateTime.now())
                        .build();
                searchInboxMapper.insert(entity);
            }
        } catch (Exception e) {
            log.error("Failed to record search_sync_inbox for message [{}]: {}", messageId, e.getMessage(), e);
        }
    }

    private CourseIndexDoc getCourseDocFromEs(String indexName, String courseId) {
        try {
            GetResponse<CourseIndexDoc> response = elasticsearchClient.get(
                    g -> g.index(indexName).id(courseId), CourseIndexDoc.class);
            if (response != null && response.found() && response.source() != null) {
                return response.source();
            }
        } catch (Exception e) {
            log.debug("Doc [{}] not found or error getting from ES: {}", courseId, e.getMessage());
        }
        return null;
    }

    private boolean isOfflineOrDeletedEvent(String eventType, String status) {
        if ("CourseOfflined".equalsIgnoreCase(eventType)
                || "CourseOffline".equalsIgnoreCase(eventType)
                || "CourseDeleted".equalsIgnoreCase(eventType)
                || "CourseArchived".equalsIgnoreCase(eventType)) {
            return true;
        }
        if ("OFFLINE".equalsIgnoreCase(status)
                || "DELETED".equalsIgnoreCase(status)
                || "ARCHIVED".equalsIgnoreCase(status)) {
            return true;
        }
        return StringUtils.hasText(status) && !"PUBLISHED".equalsIgnoreCase(status);
    }

    private String resolveCourseId(CourseDomainEvent event) {
        if (event.getData() != null && event.getData().getCourseId() != null) {
            return String.valueOf(event.getData().getCourseId());
        }
        if (StringUtils.hasText(event.getAggregateId())) {
            return event.getAggregateId().trim();
        }
        return null;
    }

    private String resolveContentCourseId(ContentDomainEvent event) {
        if (event.getData() != null && event.getData().getCourseId() != null) {
            return String.valueOf(event.getData().getCourseId());
        }
        if ("Course".equalsIgnoreCase(event.getAggregateType()) && StringUtils.hasText(event.getAggregateId())) {
            return event.getAggregateId().trim();
        }
        return null;
    }

    private String serializePayload(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
