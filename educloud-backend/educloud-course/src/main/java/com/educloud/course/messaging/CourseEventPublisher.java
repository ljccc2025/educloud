package com.educloud.course.messaging;

import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 课程域事件发布器（M05 任务 9）：包装 {@link OutboxWriter}，aggregateType=Course、
 * aggregateId=courseId、eventVersion=1、aggregateVersion=调用方传入（课程根乐观锁版本）。
 *
 * <p>payload 用 LinkedHashMap 保证字段顺序可测；requestId/traceId 从
 * {@link RequestContextAccessor} 解析。事件在业务事务内写入 Outbox（与业务同一本地事务
 * 提交），由任务 15 的 dispatcher 组装 EventEnvelope 投递 RabbitMQ（routing key
 * aggregateType.aggregateId）。</p>
 */
@Component
public class CourseEventPublisher {

    private static final String AGGREGATE_TYPE = "Course";

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final RequestContextAccessor requestContextAccessor;

    public CourseEventPublisher(
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            RequestContextAccessor requestContextAccessor) {
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestContextAccessor = Objects.requireNonNull(requestContextAccessor, "requestContextAccessor");
    }

    /** 课程创建（动态流阶段 2）：同事务写 CourseCreated outbox 行，含教师归属与标题。 */
    public void courseCreated(
            Long courseId, Long versionId, Long teacherId, String title,
            long aggregateVersion, LocalDateTime createdAt) {
        writeCourseEvent("CourseCreated", "createdAt", courseId, versionId, teacherId, title, aggregateVersion, createdAt);
    }

    /** 课程修改（动态流阶段 2）：草稿更新成功后同事务写 CourseUpdated outbox 行。 */
    public void courseUpdated(
            Long courseId, Long versionId, Long teacherId, String title,
            long aggregateVersion, LocalDateTime updatedAt) {
        writeCourseEvent("CourseUpdated", "updatedAt", courseId, versionId, teacherId, title, aggregateVersion, updatedAt);
    }

    /**
     * 课程审核通过并发布：同事务写 CoursePublished outbox 行。
     * 动态流阶段 2：payload 补发教师归属与课程标题（analytics 教师侧动态依赖）。
     */
    public void coursePublished(
            Long courseId, Long versionId, Long teacherId, String title,
            long aggregateVersion, LocalDateTime publishedAt) {
        writeCourseEvent("CoursePublished", "publishedAt", courseId, versionId, teacherId, title, aggregateVersion, publishedAt);
    }

    /** 课程下架（任务 10）：PUBLISHED→OFFLINE 后同事务写 CourseOfflined outbox 行。 */
    public void courseOfflined(
            Long courseId, Long versionId, long aggregateVersion, LocalDateTime offlinedAt) {
        writeCourseEvent("CourseOfflined", "offlinedAt", courseId, versionId, null, null, aggregateVersion, offlinedAt);
    }

    /** 课程重新上架（任务 10）：OFFLINE→PUBLISHED 后同事务写 CourseRepublished outbox 行。 */
    public void courseRepublished(
            Long courseId, Long versionId, long aggregateVersion, LocalDateTime republishedAt) {
        writeCourseEvent("CourseRepublished", "republishedAt", courseId, versionId, null, null, aggregateVersion, republishedAt);
    }

    /** 课程归档（任务 10）：OFFLINE→ARCHIVED 后同事务写 CourseArchived outbox 行。 */
    public void courseArchived(
            Long courseId, Long versionId, long aggregateVersion, LocalDateTime archivedAt) {
        writeCourseEvent("CourseArchived", "archivedAt", courseId, versionId, null, null, aggregateVersion, archivedAt);
    }

    /**
     * 课程评价（动态流阶段 2）：评价 upsert 后同事务写 CourseReviewed outbox 行，
     * 含 rating；aggregateType=CourseReview、aggregateId=reviewId。
     */
    public void courseReviewed(
            Long courseId,
            Long reviewId,
            Long studentId,
            Long teacherId,
            Integer rating,
            long aggregateVersion,
            LocalDateTime reviewedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("reviewId", reviewId);
        payload.put("studentId", studentId);
        payload.put("teacherId", teacherId);
        payload.put("rating", rating);
        payload.put("version", aggregateVersion);
        payload.put("reviewedAt", reviewedAt.toString());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "failed to serialize CourseReviewed payload: " + reviewId, failure);
        }
        outboxWriter.write(
                "CourseReview",
                String.valueOf(reviewId),
                "CourseReviewed",
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }

    /**
     * 免费选课成功（M05 任务 13）：同事务写 EnrollmentCreated outbox 行。
     * aggregateType=Enrollment、aggregateId=enrollmentId、aggregateVersion=enrollment.version；
     * payload 固定 courseId/studentId/source/version/enrolledAt（LinkedHashMap 保序可测）。
     * 动态流阶段 2 补发：teacherId（课程归属教师）与 courseTitle（课程标题快照），
     * 供 analytics 教师侧/学生侧报名动态映射（规格 §4.1）；均可为 null（消费者降级）。
     */
    public void enrollmentCreated(
            Long enrollmentId,
            Long courseId,
            Long studentId,
            String source,
            Long teacherId,
            String courseTitle,
            long aggregateVersion,
            LocalDateTime enrolledAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("studentId", studentId);
        payload.put("source", source);
        if (teacherId != null) {
            payload.put("teacherId", teacherId);
        }
        if (courseTitle != null) {
            payload.put("courseTitle", courseTitle);
        }
        payload.put("version", aggregateVersion);
        payload.put("enrolledAt", enrolledAt.toString());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "failed to serialize EnrollmentCreated payload: " + enrollmentId, failure);
        }
        outboxWriter.write(
                "Enrollment",
                String.valueOf(enrollmentId),
                "EnrollmentCreated",
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }

    /**
     * 生命周期事件公共落库：payload 固定 courseId/versionId/时间字段（LinkedHashMap 保序可测）；
     * 动态流阶段 2：可选补发教师归属（teacherId）与课程标题（title），兼容 analytics
     * 教师侧动态映射（teacherId 缺失时消费者跳过教师行动态）。
     */
    private void writeCourseEvent(
            String eventType,
            String timestampField,
            Long courseId,
            Long versionId,
            Long teacherId,
            String title,
            long aggregateVersion,
            LocalDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("versionId", versionId);
        if (teacherId != null) {
            payload.put("teacherId", teacherId);
        }
        if (title != null) {
            payload.put("title", title);
        }
        payload.put(timestampField, occurredAt.toString());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "failed to serialize " + eventType + " payload: " + courseId, failure);
        }
        outboxWriter.write(
                AGGREGATE_TYPE,
                String.valueOf(courseId),
                eventType,
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }
}
