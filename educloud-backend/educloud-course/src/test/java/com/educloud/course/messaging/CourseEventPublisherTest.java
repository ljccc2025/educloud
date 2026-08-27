package com.educloud.course.messaging;

import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 15：CourseEventPublisher 单元测试（mock OutboxWriter + RequestContextAccessor）。
 *
 * <p>依据：M05 计划任务 9/15 —— 六个发布方法包装 {@link OutboxWriter.write}：
 * 生命周期事件 aggregateType=Course、aggregateId=courseId、eventVersion=1、
 * aggregateVersion=调用方传入（课程根乐观锁版本）；选课事件 aggregateType=Enrollment、
 * aggregateId=enrollmentId、payload 固定字段（LinkedHashMap 保序可测）；requestId/traceId
 * 从 {@link RequestContextAccessor} 解析。Outbox 落库字段即信封字段（EventEnvelope 由
 * dispatcher 组装，见 OutboxEventDispatcherTest）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CourseEventPublisherTest {

    private static final long COURSE_ID = 10001L;
    private static final long VERSION_ID = 90001L;
    private static final long ENROLLMENT_ID = 70001L;
    private static final long STUDENT_ID = 30001L;
    private static final long TEACHER_ID = 20001L;
    private static final long REVIEW_ID = 80001L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 23, 10, 30);

    @Mock
    private OutboxWriter outboxWriter;
    @Mock
    private RequestContextAccessor requestContextAccessor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CourseEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CourseEventPublisher(outboxWriter, objectMapper, requestContextAccessor);
        when(requestContextAccessor.requestId()).thenReturn("req-1");
        when(requestContextAccessor.traceId()).thenReturn(Optional.of("trace-1"));
    }

    @Test
    void coursePublishedWritesOutboxRowWithEnvelopeFieldsAndPayload() throws Exception {
        publisher.coursePublished(COURSE_ID, VERSION_ID, TEACHER_ID, "微服务实践", 2L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("Course");
        assertThat(write.aggregateId).isEqualTo("10001");
        assertThat(write.eventType).isEqualTo("CoursePublished");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(2L);
        assertThat(write.requestId).isEqualTo("req-1");
        assertThat(write.traceId).isEqualTo("trace-1");

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("versionId").asLong()).isEqualTo(VERSION_ID);
        // 动态流阶段 2：补发教师归属与标题（analytics 教师侧动态依赖）。
        assertThat(payload.get("teacherId").asLong()).isEqualTo(TEACHER_ID);
        assertThat(payload.get("title").asText()).isEqualTo("微服务实践");
        assertThat(payload.get("publishedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void courseCreatedWritesOutboxRowWithTeacherAndTitle() throws Exception {
        publisher.courseCreated(COURSE_ID, VERSION_ID, TEACHER_ID, "新建课程", 0L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("Course");
        assertThat(write.aggregateId).isEqualTo("10001");
        assertThat(write.eventType).isEqualTo("CourseCreated");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(0L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("versionId").asLong()).isEqualTo(VERSION_ID);
        assertThat(payload.get("teacherId").asLong()).isEqualTo(TEACHER_ID);
        assertThat(payload.get("title").asText()).isEqualTo("新建课程");
        assertThat(payload.get("createdAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void courseUpdatedWritesOutboxRowWithTeacherAndTitle() throws Exception {
        publisher.courseUpdated(COURSE_ID, VERSION_ID, TEACHER_ID, "修改后标题", 3L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("Course");
        assertThat(write.eventType).isEqualTo("CourseUpdated");
        assertThat(write.aggregateVersion).isEqualTo(3L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("teacherId").asLong()).isEqualTo(TEACHER_ID);
        assertThat(payload.get("title").asText()).isEqualTo("修改后标题");
        assertThat(payload.get("updatedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void courseReviewedWritesOutboxRowWithRating() throws Exception {
        publisher.courseReviewed(COURSE_ID, REVIEW_ID, STUDENT_ID, TEACHER_ID, 5, 4L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("CourseReview");
        assertThat(write.aggregateId).isEqualTo("80001");
        assertThat(write.eventType).isEqualTo("CourseReviewed");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(4L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("reviewId").asLong()).isEqualTo(REVIEW_ID);
        assertThat(payload.get("studentId").asLong()).isEqualTo(STUDENT_ID);
        assertThat(payload.get("teacherId").asLong()).isEqualTo(TEACHER_ID);
        assertThat(payload.get("rating").asInt()).isEqualTo(5);
        assertThat(payload.get("reviewedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void courseOfflinedWritesOutboxRowWithEnvelopeFieldsAndPayload() throws Exception {
        publisher.courseOfflined(COURSE_ID, VERSION_ID, 3L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("Course");
        assertThat(write.aggregateId).isEqualTo("10001");
        assertThat(write.eventType).isEqualTo("CourseOfflined");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(3L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("versionId").asLong()).isEqualTo(VERSION_ID);
        assertThat(payload.get("offlinedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void courseRepublishedWritesOutboxRowWithEnvelopeFieldsAndPayload() throws Exception {
        publisher.courseRepublished(COURSE_ID, VERSION_ID, 4L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("Course");
        assertThat(write.aggregateId).isEqualTo("10001");
        assertThat(write.eventType).isEqualTo("CourseRepublished");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(4L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("versionId").asLong()).isEqualTo(VERSION_ID);
        assertThat(payload.get("republishedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void courseArchivedWritesOutboxRowWithEnvelopeFieldsAndPayload() throws Exception {
        publisher.courseArchived(COURSE_ID, VERSION_ID, 5L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("Course");
        assertThat(write.aggregateId).isEqualTo("10001");
        assertThat(write.eventType).isEqualTo("CourseArchived");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(5L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("versionId").asLong()).isEqualTo(VERSION_ID);
        assertThat(payload.get("archivedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void enrollmentCreatedWritesOutboxRowWithEnrollmentAggregateAndPayload() throws Exception {
        publisher.enrollmentCreated(
                ENROLLMENT_ID, COURSE_ID, STUDENT_ID, "FREE", 1L, OCCURRED_AT);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("Enrollment");
        assertThat(write.aggregateId).isEqualTo("70001");
        assertThat(write.eventType).isEqualTo("EnrollmentCreated");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(1L);
        assertThat(write.requestId).isEqualTo("req-1");
        assertThat(write.traceId).isEqualTo("trace-1");

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("studentId").asLong()).isEqualTo(STUDENT_ID);
        assertThat(payload.get("source").asText()).isEqualTo("FREE");
        assertThat(payload.get("version").asLong()).isEqualTo(1L);
        assertThat(payload.get("enrolledAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void absentTraceIdIsPassedAsNull() {
        when(requestContextAccessor.traceId()).thenReturn(Optional.empty());
        publisher.coursePublished(COURSE_ID, VERSION_ID, TEACHER_ID, "微服务实践", 1L, OCCURRED_AT);

        ArgumentCaptor<String> traceId = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).write(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                traceId.capture());
        assertThat(traceId.getValue()).isNull();
    }

    private CapturedWrite captureWrite() {
        ArgumentCaptor<String> aggregateType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> eventVersion = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Long> aggregateVersion = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> traceId = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).write(
                aggregateType.capture(),
                aggregateId.capture(),
                eventType.capture(),
                eventVersion.capture(),
                aggregateVersion.capture(),
                payloadJson.capture(),
                requestId.capture(),
                traceId.capture());
        return new CapturedWrite(
                aggregateType.getValue(),
                aggregateId.getValue(),
                eventType.getValue(),
                eventVersion.getValue(),
                aggregateVersion.getValue(),
                payloadJson.getValue(),
                requestId.getValue(),
                traceId.getValue());
    }

    private record CapturedWrite(
            String aggregateType,
            String aggregateId,
            String eventType,
            int eventVersion,
            long aggregateVersion,
            String payloadJson,
            String requestId,
            String traceId) {
    }
}
