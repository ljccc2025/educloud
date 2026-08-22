package com.educloud.file.messaging;

import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 11：FileEventPublisher 单元测试（mock OutboxWriter + RequestContextAccessor）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 11 —— 四个文件事件方法包装
 * OutboxWriter.write：aggregateType=FileObject、aggregateId=fileId、eventVersion=1、
 * aggregateVersion=调用方传入（文件根版本）、payload 为 JSON（LinkedHashMap 保序，
 * 字段完整），requestId/traceId 从 RequestContextAccessor 解析。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileEventPublisherTest {

    private static final long FILE_ID = 1001L;
    private static final String OBJECT_KEY = "educloud-files/user-42/20260822/abc.png";
    private static final String OWNER_SERVICE = "user";
    private static final String OWNER_TYPE = "USER_PROFILE";
    private static final String OWNER_ID = "u-42";
    private static final long UPLOADER_ID = 42L;
    private static final String REASON = "manual-cleanup";

    @Mock
    private OutboxWriter outboxWriter;
    @Mock
    private RequestContextAccessor requestContextAccessor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FileEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new FileEventPublisher(outboxWriter, objectMapper, requestContextAccessor);
        when(requestContextAccessor.requestId()).thenReturn("req-1");
        when(requestContextAccessor.traceId()).thenReturn(Optional.of("trace-1"));
    }

    @Test
    void fileUploadedWritesEnvelopeFieldsAndCompletePayload() throws Exception {
        publisher.fileUploaded(FILE_ID, OBJECT_KEY, null, UPLOADER_ID, 1L);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("FileObject");
        assertThat(write.aggregateId).isEqualTo("1001");
        assertThat(write.eventType).isEqualTo("FileUploaded");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(1L);
        assertThat(write.requestId).isEqualTo("req-1");
        assertThat(write.traceId).isEqualTo("trace-1");

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("fileId").asLong()).isEqualTo(FILE_ID);
        assertThat(payload.get("objectKey").asText()).isEqualTo(OBJECT_KEY);
        assertThat(payload.get("ownerService").isNull()).isTrue();
        assertThat(payload.get("uploaderId").asLong()).isEqualTo(UPLOADER_ID);
    }

    @Test
    void fileBoundWritesEnvelopeFieldsAndCompletePayload() throws Exception {
        publisher.fileBound(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID, 2L);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("FileObject");
        assertThat(write.aggregateId).isEqualTo("1001");
        assertThat(write.eventType).isEqualTo("FileBound");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(2L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("fileId").asLong()).isEqualTo(FILE_ID);
        assertThat(payload.get("ownerService").asText()).isEqualTo(OWNER_SERVICE);
        assertThat(payload.get("ownerType").asText()).isEqualTo(OWNER_TYPE);
        assertThat(payload.get("ownerId").asText()).isEqualTo(OWNER_ID);
    }

    @Test
    void fileUnboundWritesEnvelopeFieldsAndCompletePayload() throws Exception {
        publisher.fileUnbound(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID, 3L);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("FileObject");
        assertThat(write.aggregateId).isEqualTo("1001");
        assertThat(write.eventType).isEqualTo("FileUnbound");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(3L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("fileId").asLong()).isEqualTo(FILE_ID);
        assertThat(payload.get("ownerService").asText()).isEqualTo(OWNER_SERVICE);
        assertThat(payload.get("ownerType").asText()).isEqualTo(OWNER_TYPE);
        assertThat(payload.get("ownerId").asText()).isEqualTo(OWNER_ID);
    }

    @Test
    void fileDeletedWritesEnvelopeFieldsAndCompletePayload() throws Exception {
        publisher.fileDeleted(
                FILE_ID, OBJECT_KEY, OWNER_SERVICE, OWNER_TYPE, OWNER_ID, REASON, 4L);

        CapturedWrite write = captureWrite();
        assertThat(write.aggregateType).isEqualTo("FileObject");
        assertThat(write.aggregateId).isEqualTo("1001");
        assertThat(write.eventType).isEqualTo("FileDeleted");
        assertThat(write.eventVersion).isEqualTo(1);
        assertThat(write.aggregateVersion).isEqualTo(4L);

        JsonNode payload = objectMapper.readTree(write.payloadJson);
        assertThat(payload.get("fileId").asLong()).isEqualTo(FILE_ID);
        assertThat(payload.get("objectKey").asText()).isEqualTo(OBJECT_KEY);
        assertThat(payload.get("ownerService").asText()).isEqualTo(OWNER_SERVICE);
        assertThat(payload.get("ownerType").asText()).isEqualTo(OWNER_TYPE);
        assertThat(payload.get("ownerId").asText()).isEqualTo(OWNER_ID);
        assertThat(payload.get("reason").asText()).isEqualTo(REASON);
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
