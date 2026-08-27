package com.educloud.search.service;

import com.educloud.search.entity.IndexSyncFailureEntity;
import com.educloud.search.mapper.IndexSyncFailureMapper;
import com.educloud.search.messaging.event.CourseDomainEvent;
import com.educloud.search.service.impl.DlqRecoveryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqRecoveryServiceTest {

    @Mock
    private IndexSyncFailureMapper failureMapper;
    @Mock
    private IndexSyncService indexSyncService;

    private ObjectMapper objectMapper;
    private DlqRecoveryServiceImpl dlqRecoveryService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        dlqRecoveryService = new DlqRecoveryServiceImpl(failureMapper, indexSyncService, objectMapper);
    }

    private Message buildDeadLetterMessage() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(88L);
        properties.setHeader("x-death", List.of(Map.of(
                "exchange", "educloud.course.events",
                "routing-keys", List.of("course.published"),
                "count", 2,
                "reason", "rejected"
        )));
        String payload = "{\"messageId\":\"msg_dlq_001\",\"eventType\":\"CoursePublished\","
                + "\"aggregateType\":\"Course\",\"aggregateId\":\"1001\",\"aggregateVersion\":1,"
                + "\"data\":{\"courseId\":1001,\"title\":\"测试课程\",\"lifecycleStatus\":\"PUBLISHED\"}}";
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    private IndexSyncFailureEntity buildPendingRecord(String messageId, String payload, String routingKey, int retryCount) {
        return IndexSyncFailureEntity.builder()
                .id(9001L)
                .messageId(messageId)
                .exchange("educloud.course.events")
                .routingKey(routingKey)
                .payload(payload)
                .error("Elasticsearch index failed")
                .retryCount(retryCount)
                .status(DlqRecoveryService.STATUS_PENDING)
                .build();
    }

    @Test
    @DisplayName("测试死信落库：解析 x-death 头并插入 PENDING 记录")
    void testRecordFailure_InsertsPendingRecord() {
        when(failureMapper.selectList(any())).thenReturn(List.of());

        dlqRecoveryService.recordFailure(buildDeadLetterMessage());

        ArgumentCaptor<IndexSyncFailureEntity> captor = ArgumentCaptor.forClass(IndexSyncFailureEntity.class);
        verify(failureMapper, times(1)).insert(captor.capture());
        IndexSyncFailureEntity saved = captor.getValue();
        assertThat(saved.getMessageId()).isEqualTo("msg_dlq_001");
        assertThat(saved.getExchange()).isEqualTo("educloud.course.events");
        assertThat(saved.getRoutingKey()).isEqualTo("course.published");
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(DlqRecoveryService.STATUS_PENDING);
        assertThat(saved.getPayload()).contains("msg_dlq_001");
        assertThat(saved.getError()).isNotBlank();
    }

    @Test
    @DisplayName("测试死信落库：同 messageId 已存在记录时更新而非重复插入")
    void testRecordFailure_UpdatesExistingRecord() {
        IndexSyncFailureEntity existing = buildPendingRecord("msg_dlq_001",
                "{\"messageId\":\"msg_dlq_001\"}", "course.published", 1);
        when(failureMapper.selectList(any())).thenReturn(List.of(existing));

        dlqRecoveryService.recordFailure(buildDeadLetterMessage());

        verify(failureMapper, never()).insert(any(IndexSyncFailureEntity.class));
        verify(failureMapper, times(1)).updateById(existing);
        assertThat(existing.getRetryCount()).isEqualTo(2);
        assertThat(existing.getPayload()).contains("aggregateVersion");
    }

    @Test
    @DisplayName("测试死信落库异常不向外抛出（避免 DLQ 无限循环）")
    void testRecordFailure_MapperFailure_DoesNotThrow() {
        when(failureMapper.selectList(any())).thenThrow(new RuntimeException("DB unavailable"));

        assertThatCode(() -> dlqRecoveryService.recordFailure(buildDeadLetterMessage()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试定时重放成功：PENDING 记录转 RESOLVED")
    void testReplayPending_SuccessMarksResolved() {
        IndexSyncFailureEntity record = buildPendingRecord("msg_dlq_001",
                "{\"messageId\":\"msg_dlq_001\",\"eventType\":\"CoursePublished\","
                        + "\"aggregateType\":\"Course\",\"aggregateId\":\"1001\",\"aggregateVersion\":1,"
                        + "\"data\":{\"courseId\":1001,\"lifecycleStatus\":\"PUBLISHED\"}}",
                "course.published", 1);
        when(failureMapper.selectList(any())).thenReturn(List.of(record));

        int settled = dlqRecoveryService.replayPending();

        assertThat(settled).isEqualTo(1);
        verify(indexSyncService, times(1)).handleCourseEvent(any(CourseDomainEvent.class));
        verify(failureMapper, times(1)).updateById(record);
        assertThat(record.getStatus()).isEqualTo(DlqRecoveryService.STATUS_RESOLVED);
    }

    @Test
    @DisplayName("测试定时重放失败：累加重试次数并保持 PENDING")
    void testReplayPending_FailureIncrementsRetryCount() {
        IndexSyncFailureEntity record = buildPendingRecord("msg_dlq_002",
                "{\"messageId\":\"msg_dlq_002\",\"eventType\":\"CourseUpdated\","
                        + "\"aggregateType\":\"Course\",\"aggregateId\":\"2001\",\"aggregateVersion\":2}",
                "course.updated", 0);
        when(failureMapper.selectList(any())).thenReturn(List.of(record));
        doThrow(new RuntimeException("ES cluster unavailable"))
                .when(indexSyncService).handleCourseEvent(any(CourseDomainEvent.class));

        int settled = dlqRecoveryService.replayPending();

        assertThat(settled).isZero();
        verify(failureMapper, times(1)).updateById(record);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo(DlqRecoveryService.STATUS_PENDING);
        assertThat(record.getError()).contains("ES cluster unavailable");
    }

    @Test
    @DisplayName("测试连续失败超过 5 次：标记 DEAD 不再自动重试")
    void testReplayPending_ExceededMaxRetries_MarksDead() {
        IndexSyncFailureEntity record = buildPendingRecord("msg_dlq_003",
                "{\"messageId\":\"msg_dlq_003\",\"eventType\":\"CoursePublished\","
                        + "\"aggregateType\":\"Course\",\"aggregateId\":\"3001\",\"aggregateVersion\":1}",
                "course.published", DlqRecoveryServiceImpl.MAX_RETRY_COUNT - 1);
        when(failureMapper.selectList(any())).thenReturn(List.of(record));
        doThrow(new RuntimeException("ES cluster unavailable"))
                .when(indexSyncService).handleCourseEvent(any(CourseDomainEvent.class));

        dlqRecoveryService.replayPending();

        assertThat(record.getRetryCount()).isEqualTo(DlqRecoveryServiceImpl.MAX_RETRY_COUNT);
        assertThat(record.getStatus()).isEqualTo(DlqRecoveryService.STATUS_DEAD);
    }

    @Test
    @DisplayName("测试手动重放：按 id 重放成功转 RESOLVED")
    void testReplayById_Success() {
        IndexSyncFailureEntity record = buildPendingRecord("msg_dlq_004",
                "{\"messageId\":\"msg_dlq_004\",\"eventType\":\"CoursePublished\","
                        + "\"aggregateType\":\"Course\",\"aggregateId\":\"4001\",\"aggregateVersion\":1}",
                "course.published", 0);
        when(failureMapper.selectById(9001L)).thenReturn(record);

        DlqRecoveryService.ReplayResult result = dlqRecoveryService.replayById(9001L);

        assertThat(result.status()).isEqualTo(DlqRecoveryService.STATUS_RESOLVED);
        verify(indexSyncService, times(1)).handleCourseEvent(any(CourseDomainEvent.class));
        assertThat(record.getStatus()).isEqualTo(DlqRecoveryService.STATUS_RESOLVED);
    }

    @Test
    @DisplayName("测试手动重放：记录不存在返回 NOT_FOUND")
    void testReplayById_NotFound() {
        when(failureMapper.selectById(9999L)).thenReturn(null);

        DlqRecoveryService.ReplayResult result = dlqRecoveryService.replayById(9999L);

        assertThat(result.status()).isEqualTo("NOT_FOUND");
        verify(indexSyncService, never()).handleCourseEvent(any());
        verify(indexSyncService, never()).handleContentEvent(any());
    }
}
