package com.educloud.file.messaging;

import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 文件领域事件发布器：包装 {@link OutboxWriter}，aggregateType=FileObject、
 * aggregateId=fileId、eventVersion=1、aggregateVersion=调用方传入（文件根版本）。
 *
 * <p>payload 用 LinkedHashMap 保证字段顺序可测；requestId/traceId 从
 * {@link RequestContextAccessor} 解析。事件在业务事务内写入 Outbox（与业务同一
 * 本地事务提交），由 {@link OutboxEventDispatcher} 投递 RabbitMQ。</p>
 */
@Component
public class FileEventPublisher {

    private static final String AGGREGATE_TYPE = "FileObject";

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final RequestContextAccessor requestContextAccessor;

    public FileEventPublisher(
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            RequestContextAccessor requestContextAccessor) {
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestContextAccessor = Objects.requireNonNull(requestContextAccessor, "requestContextAccessor");
    }

    public void fileUploaded(
            Long fileId, String objectKey, String ownerService, Long uploaderId, long aggregateVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", fileId);
        payload.put("objectKey", objectKey);
        payload.put("ownerService", ownerService);
        payload.put("uploaderId", uploaderId);
        publish(fileId, "FileUploaded", aggregateVersion, payload);
    }

    public void fileBound(
            Long fileId, String ownerService, String ownerType, String ownerId, long aggregateVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", fileId);
        payload.put("ownerService", ownerService);
        payload.put("ownerType", ownerType);
        payload.put("ownerId", ownerId);
        publish(fileId, "FileBound", aggregateVersion, payload);
    }

    public void fileUnbound(
            Long fileId, String ownerService, String ownerType, String ownerId, long aggregateVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", fileId);
        payload.put("ownerService", ownerService);
        payload.put("ownerType", ownerType);
        payload.put("ownerId", ownerId);
        publish(fileId, "FileUnbound", aggregateVersion, payload);
    }

    public void fileDeleted(
            Long fileId, String objectKey, String ownerService, String ownerType, String ownerId,
            String reason, long aggregateVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", fileId);
        payload.put("objectKey", objectKey);
        payload.put("ownerService", ownerService);
        payload.put("ownerType", ownerType);
        payload.put("ownerId", ownerId);
        payload.put("reason", reason);
        publish(fileId, "FileDeleted", aggregateVersion, payload);
    }

    private void publish(
            Long fileId, String eventType, long aggregateVersion, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "failed to serialize file event payload: " + eventType, failure);
        }
        outboxWriter.write(
                AGGREGATE_TYPE,
                String.valueOf(fileId),
                eventType,
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }
}
