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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("versionId", versionId);
        payload.put("publishedAt", publishedAt.toString());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "failed to serialize CoursePublished payload: " + courseId, failure);
        }
        outboxWriter.write(
                AGGREGATE_TYPE,
                String.valueOf(courseId),
                "CoursePublished",
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }
}
