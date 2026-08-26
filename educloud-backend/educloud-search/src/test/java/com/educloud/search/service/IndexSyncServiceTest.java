package com.educloud.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.document.LessonDoc;
import com.educloud.search.entity.SearchInboxEntity;
import com.educloud.search.mapper.SearchInboxMapper;
import com.educloud.search.messaging.event.ContentDomainEvent;
import com.educloud.search.messaging.event.CourseDomainEvent;
import com.educloud.search.service.impl.IndexSyncServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexSyncServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private SearchInboxMapper searchInboxMapper;

    private ElasticsearchProperties properties;
    private ObjectMapper objectMapper;
    private IndexSyncServiceImpl indexSyncService;

    @BeforeEach
    void setUp() {
        properties = new ElasticsearchProperties();
        properties.setAliasName("educloud_course_search");
        objectMapper = new ObjectMapper();
        indexSyncService = new IndexSyncServiceImpl(
                elasticsearchClient, properties, searchInboxMapper, objectMapper);
    }

    @Test
    @DisplayName("测试课程增量发布同步 (CoursePublished -> ES 创建索引文档与 Inbox 落库)")
    void testCoursePublished_CreatesNewDocInEs() throws Exception {
        CourseDomainEvent event = CourseDomainEvent.builder()
                .messageId("msg_pub_001")
                .eventType("CoursePublished")
                .aggregateType("Course")
                .aggregateId("1001")
                .aggregateVersion(1L)
                .data(CourseDomainEvent.CourseEventData.builder()
                        .courseId(1001L)
                        .title("Spring Cloud 微服务精讲")
                        .subtitle("从入门到实战")
                        .description("详细讲解微服务生态")
                        .teacherId("9001")
                        .teacherName("王老师")
                        .category("后端开发")
                        .categoryCode("BACKEND")
                        .priceCents(9900L)
                        .isFree(false)
                        .rating(5.0f)
                        .studentCount(100)
                        .lessonCount(10)
                        .lifecycleStatus("PUBLISHED")
                        .tags(List.of("Java", "Spring Cloud"))
                        .publishedAt(LocalDateTime.now())
                        .build())
                .build();

        // 幂等查询返回 null
        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // ES 文档不存在
        GetResponse<CourseIndexDoc> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(false);
        when(elasticsearchClient.get(any(Function.class), eq(CourseIndexDoc.class))).thenReturn(mockGetResponse);

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(elasticsearchClient.index(any(Function.class))).thenReturn(mockIndexResponse);

        indexSyncService.handleCourseEvent(event);

        verify(elasticsearchClient, times(1)).index(any(Function.class));
        ArgumentCaptor<SearchInboxEntity> inboxCaptor = ArgumentCaptor.forClass(SearchInboxEntity.class);
        verify(searchInboxMapper, times(1)).insert(inboxCaptor.capture());

        SearchInboxEntity savedInbox = inboxCaptor.getValue();
        assertThat(savedInbox.getMessageId()).isEqualTo("msg_pub_001");
        assertThat(savedInbox.getEventType()).isEqualTo("CoursePublished");
        assertThat(savedInbox.getAggregateId()).isEqualTo("1001");
        assertThat(savedInbox.getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("测试课程更新同步 (CourseUpdated -> 合并现有 ES 文档并写回)")
    void testCourseUpdated_MergesIntoExistingDoc() throws Exception {
        CourseIndexDoc existingDoc = CourseIndexDoc.builder()
                .id("1001")
                .courseId("1001")
                .title("老标题")
                .priceCents(5000L)
                .aggregateVersion(1L)
                .status("PUBLISHED")
                .build();

        CourseDomainEvent event = CourseDomainEvent.builder()
                .messageId("msg_upd_002")
                .eventType("CourseUpdated")
                .aggregateType("Course")
                .aggregateId("1001")
                .aggregateVersion(2L)
                .data(CourseDomainEvent.CourseEventData.builder()
                        .courseId(1001L)
                        .title("全新 Spring Cloud 实战 2026")
                        .priceCents(19900L)
                        .lifecycleStatus("PUBLISHED")
                        .build())
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        GetResponse<CourseIndexDoc> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(true);
        when(mockGetResponse.source()).thenReturn(existingDoc);
        when(elasticsearchClient.get(any(Function.class), eq(CourseIndexDoc.class))).thenReturn(mockGetResponse);

        indexSyncService.handleCourseEvent(event);

        verify(elasticsearchClient, times(1)).index(any(Function.class));
        ArgumentCaptor<SearchInboxEntity> inboxCaptor = ArgumentCaptor.forClass(SearchInboxEntity.class);
        verify(searchInboxMapper, times(1)).insert(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("测试课程下架同步 (CourseOfflined -> 自动从 ES 中删除索引文档)")
    void testCourseOfflined_DeletesFromEs() throws Exception {
        CourseDomainEvent event = CourseDomainEvent.builder()
                .messageId("msg_off_003")
                .eventType("CourseOfflined")
                .aggregateType("Course")
                .aggregateId("1001")
                .aggregateVersion(3L)
                .data(CourseDomainEvent.CourseEventData.builder()
                        .courseId(1001L)
                        .lifecycleStatus("OFFLINE")
                        .build())
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        when(elasticsearchClient.delete(any(Function.class))).thenReturn(mockDeleteResponse);

        indexSyncService.handleCourseEvent(event);

        verify(elasticsearchClient, times(1)).delete(any(Function.class));
        ArgumentCaptor<SearchInboxEntity> inboxCaptor = ArgumentCaptor.forClass(SearchInboxEntity.class);
        verify(searchInboxMapper, times(1)).insert(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("测试课件发布/添加同步 (LessonPublished -> 局部更新课件列表与 lessonCount)")
    void testLessonPublished_AddsLessonToDoc() throws Exception {
        CourseIndexDoc existingDoc = CourseIndexDoc.builder()
                .id("1001")
                .courseId("1001")
                .title("微服务架构")
                .aggregateVersion(2L)
                .lessons(new ArrayList<>())
                .lessonCount(0)
                .status("PUBLISHED")
                .build();

        ContentDomainEvent event = ContentDomainEvent.builder()
                .messageId("msg_les_004")
                .eventType("LessonPublished")
                .aggregateType("CourseContent")
                .aggregateId("7001")
                .aggregateVersion(3L)
                .data(ContentDomainEvent.ContentEventData.builder()
                        .courseId(1001L)
                        .lessonId(5001L)
                        .title("01. 服务注册与发现")
                        .chapterTitle("第一章 微服务基础")
                        .isPreview(true)
                        .build())
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        GetResponse<CourseIndexDoc> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(true);
        when(mockGetResponse.source()).thenReturn(existingDoc);
        when(elasticsearchClient.get(any(Function.class), eq(CourseIndexDoc.class))).thenReturn(mockGetResponse);

        indexSyncService.handleContentEvent(event);

        verify(elasticsearchClient, times(1)).index(any(Function.class));
        assertThat(existingDoc.getLessons()).hasSize(1);
        assertThat(existingDoc.getLessonCount()).isEqualTo(1);
        assertThat(existingDoc.getLessons().get(0).getTitle()).isEqualTo("01. 服务注册与发现");
        assertThat(existingDoc.getLessons().get(0).getIsPreview()).isTrue();

        ArgumentCaptor<SearchInboxEntity> inboxCaptor = ArgumentCaptor.forClass(SearchInboxEntity.class);
        verify(searchInboxMapper, times(1)).insert(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("测试课件删除同步 (LessonDeleted -> 局部从课件列表中剔除)")
    void testLessonDeleted_RemovesLessonFromDoc() throws Exception {
        List<LessonDoc> initialLessons = new ArrayList<>(List.of(
                LessonDoc.builder().id("5001").title("01. 注册中心").build(),
                LessonDoc.builder().id("5002").title("02. 配置中心").build()
        ));

        CourseIndexDoc existingDoc = CourseIndexDoc.builder()
                .id("1001")
                .courseId("1001")
                .aggregateVersion(2L)
                .lessons(initialLessons)
                .lessonCount(2)
                .status("PUBLISHED")
                .build();

        ContentDomainEvent event = ContentDomainEvent.builder()
                .messageId("msg_les_del_005")
                .eventType("LessonDeleted")
                .aggregateType("CourseContent")
                .aggregateId("7001")
                .aggregateVersion(3L)
                .data(ContentDomainEvent.ContentEventData.builder()
                        .courseId(1001L)
                        .lessonId(5001L)
                        .build())
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        GetResponse<CourseIndexDoc> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(true);
        when(mockGetResponse.source()).thenReturn(existingDoc);
        when(elasticsearchClient.get(any(Function.class), eq(CourseIndexDoc.class))).thenReturn(mockGetResponse);

        indexSyncService.handleContentEvent(event);

        verify(elasticsearchClient, times(1)).index(any(Function.class));
        assertThat(existingDoc.getLessons()).hasSize(1);
        assertThat(existingDoc.getLessons().get(0).getId()).isEqualTo("5002");
        assertThat(existingDoc.getLessonCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("测试课件全量修订发布 (ContentRevisionPublished -> 全量替换 lessons 列表)")
    void testContentRevisionPublished_ReplacesLessonsList() throws Exception {
        CourseIndexDoc existingDoc = CourseIndexDoc.builder()
                .id("1001")
                .courseId("1001")
                .aggregateVersion(2L)
                .lessons(new ArrayList<>(List.of(LessonDoc.builder().id("999").title("旧课件").build())))
                .lessonCount(1)
                .status("PUBLISHED")
                .build();

        List<LessonDoc> newLessons = List.of(
                LessonDoc.builder().id("6001").title("新第 1 课").chapterTitle("第一章").isPreview(true).build(),
                LessonDoc.builder().id("6002").title("新第 2 课").chapterTitle("第一章").isPreview(false).build(),
                LessonDoc.builder().id("6003").title("新第 3 课").chapterTitle("第二章").isPreview(false).build()
        );

        ContentDomainEvent event = ContentDomainEvent.builder()
                .messageId("msg_rev_006")
                .eventType("ContentRevisionPublished")
                .aggregateType("CourseContent")
                .aggregateId("7001")
                .aggregateVersion(4L)
                .data(ContentDomainEvent.ContentEventData.builder()
                        .courseId(1001L)
                        .publishedRevisionId(801L)
                        .revisionNo(2)
                        .lessons(newLessons)
                        .build())
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        GetResponse<CourseIndexDoc> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(true);
        when(mockGetResponse.source()).thenReturn(existingDoc);
        when(elasticsearchClient.get(any(Function.class), eq(CourseIndexDoc.class))).thenReturn(mockGetResponse);

        indexSyncService.handleContentEvent(event);

        verify(elasticsearchClient, times(1)).index(any(Function.class));
        assertThat(existingDoc.getLessons()).hasSize(3);
        assertThat(existingDoc.getLessonCount()).isEqualTo(3);
        assertThat(existingDoc.getLessons().get(0).getTitle()).isEqualTo("新第 1 课");
    }

    @Test
    @DisplayName("测试幂等性校验 (已消费的消息自动忽略作为 no-op)")
    void testIdempotency_AlreadyProcessedMessageSkipped() {
        SearchInboxEntity processedInbox = SearchInboxEntity.builder()
                .messageId("msg_dup_007")
                .status("PROCESSED")
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(processedInbox);

        CourseDomainEvent courseEvent = CourseDomainEvent.builder()
                .messageId("msg_dup_007")
                .eventType("CoursePublished")
                .aggregateId("1001")
                .aggregateVersion(1L)
                .build();

        indexSyncService.handleCourseEvent(courseEvent);

        ContentDomainEvent contentEvent = ContentDomainEvent.builder()
                .messageId("msg_dup_007")
                .eventType("LessonPublished")
                .aggregateId("1001")
                .aggregateVersion(1L)
                .build();

        indexSyncService.handleContentEvent(contentEvent);

        // 验证 ES 客户端从未被调用
        verifyNoInteractions(elasticsearchClient);
        verify(searchInboxMapper, never()).insert(any(SearchInboxEntity.class));
    }

    @Test
    @DisplayName("测试版本防乱序校验 (事件 aggregateVersion <= ES 现有版本号时直接丢弃并记 IGNORED)")
    void testStaleVersion_IgnoredAndRecordedAsIgnored() throws Exception {
        CourseIndexDoc existingDoc = CourseIndexDoc.builder()
                .id("1001")
                .courseId("1001")
                .aggregateVersion(5L)
                .status("PUBLISHED")
                .build();

        // 滞后事件版本 4L <= 5L
        CourseDomainEvent staleEvent = CourseDomainEvent.builder()
                .messageId("msg_stale_008")
                .eventType("CourseUpdated")
                .aggregateType("Course")
                .aggregateId("1001")
                .aggregateVersion(4L)
                .data(CourseDomainEvent.CourseEventData.builder().courseId(1001L).title("滞后的旧标题").build())
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        GetResponse<CourseIndexDoc> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(true);
        when(mockGetResponse.source()).thenReturn(existingDoc);
        when(elasticsearchClient.get(any(Function.class), eq(CourseIndexDoc.class))).thenReturn(mockGetResponse);

        indexSyncService.handleCourseEvent(staleEvent);

        // ES 索引未被更新
        verify(elasticsearchClient, never()).index(any(Function.class));

        // Inbox 记录为 IGNORED
        ArgumentCaptor<SearchInboxEntity> inboxCaptor = ArgumentCaptor.forClass(SearchInboxEntity.class);
        verify(searchInboxMapper, times(1)).insert(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getStatus()).isEqualTo("IGNORED");
        assertThat(inboxCaptor.getValue().getErrorReason()).contains("Stale version");
    }

    @Test
    @DisplayName("测试课件事件遇到不存在的课程文档时安全记录 IGNORED")
    void testContentEvent_CourseNotFoundInEs_RecordedAsIgnored() throws Exception {
        ContentDomainEvent event = ContentDomainEvent.builder()
                .messageId("msg_not_found_009")
                .eventType("LessonPublished")
                .aggregateType("CourseContent")
                .aggregateId("7001")
                .aggregateVersion(1L)
                .data(ContentDomainEvent.ContentEventData.builder().courseId(9999L).lessonId(101L).build())
                .build();

        when(searchInboxMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        GetResponse<CourseIndexDoc> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(false);
        when(elasticsearchClient.get(any(Function.class), eq(CourseIndexDoc.class))).thenReturn(mockGetResponse);

        indexSyncService.handleContentEvent(event);

        verify(elasticsearchClient, never()).index(any(Function.class));

        ArgumentCaptor<SearchInboxEntity> inboxCaptor = ArgumentCaptor.forClass(SearchInboxEntity.class);
        verify(searchInboxMapper, times(1)).insert(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getStatus()).isEqualTo("IGNORED");
    }
}
