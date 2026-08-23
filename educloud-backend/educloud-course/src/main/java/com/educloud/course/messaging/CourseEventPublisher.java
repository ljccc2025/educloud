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

    /** 课程审核通过并发布：同事务写 CoursePublished outbox 行。 */
    public void coursePublished(
            Long courseId, Long versionId, long aggregateVersion, LocalDateTime publishedAt) {
        writeLifecycleEvent("CoursePublished", "publishedAt", courseId, versionId, aggregateVersion, publishedAt);
    }

    /** 课程下架（任务 10）：PUBLISHED→OFFLINE 后同事务写 CourseOfflined outbox 行。 */
    public void courseOfflined(
            Long courseId, Long versionId, long aggregateVersion, LocalDateTime offlinedAt) {
        writeLifecycleEvent("CourseOfflined", "offlinedAt", courseId, versionId, aggregateVersion, offlinedAt);
    }

    /** 课程重新上架（任务 10）：OFFLINE→PUBLISHED 后同事务写 CourseRepublished outbox 行。 */
    public void courseRepublished(
            Long courseId, Long versionId, long aggregateVersion, LocalDateTime republishedAt) {
        writeLifecycleEvent("CourseRepublished", "republishedAt", courseId, versionId, aggregateVersion, republishedAt);
    }

    /** 课程归档（任务 10）：OFFLINE→ARCHIVED 后同事务写 CourseArchived outbox 行。 */
    public void courseArchived(
            Long courseId, Long versionId, long aggregateVersion, LocalDateTime archivedAt) {
        writeLifecycleEvent("CourseArchived", "archivedAt", courseId, versionId, aggregateVersion, archivedAt);
    }

    /**
     * 免费选课成功（M05 任务 13）：同事务写 EnrollmentCreated outbox 行。
     * aggregateType=Enrollment、aggregateId=enrollmentId、aggregateVersion=enrollment.version；
     * payload 固定 courseId/studentId/source/version/enrolledAt（LinkedHashMap 保序可测）。
     */
    public void enrollmentCreated(
            Long enrollmentId,
            Long courseId,
            Long studentId,
            String source,
            long aggregateVersion,
            LocalDateTime enrolledAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("studentId", studentId);
        payload.put("source", source);
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

    /** 生命周期事件公共落库：payload 固定 courseId/versionId/时间字段（LinkedHashMap 保序可测）。 */
    private void writeLifecycleEvent(
            String eventType,
            String timestampField,
            Long courseId,
            Long versionId,
            long aggregateVersion,
            LocalDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("versionId", versionId);
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
