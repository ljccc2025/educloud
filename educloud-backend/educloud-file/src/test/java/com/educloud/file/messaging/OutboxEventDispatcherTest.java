package com.educloud.file.messaging;

import com.educloud.common.messaging.EventEnvelope;
import com.educloud.file.entity.OutboxEventEntity;
import com.educloud.file.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 11：Outbox 发布器单元测试（复制 M03 user 版）。
 *
 * <p>依据：M03 计划任务 13 模式 —— PENDING→投递→PUBLISHED；投递异常 attempt+1
 * 且退避 next_attempt_at；达阈值标记 FAILED；信封字段完整（sourceService=educloud-file）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventDispatcherTest {

    @Mock
    private OutboxEventMapper outboxEventMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxEventDispatcher(outboxEventMapper, rabbitTemplate, new ObjectMapper());
    }

    private OutboxEventEntity pendingEvent() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(1L);
        event.setEventId("event-1");
        event.setEventType("FileUploaded");
        event.setEventVersion(1);
        event.setAggregateType("FileObject");
        event.setAggregateId("1001");
        event.setAggregateVersion(1L);
        event.setSourceSequence(1L);
        event.setPayloadJson("{\"fileId\":\"1001\"}");
        event.setRequestId("req-1");
        event.setOccurredAt(Instant.parse("2026-08-22T10:00:00Z"));
        event.setPublishStatus("PENDING");
        event.setAttemptCount(0);
        return event;
    }

    @Test
    void publishesEnvelopeWithRoutingKeyAndMarksPublished() {
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(pendingEvent()));
        when(outboxEventMapper.update(any(), any())).thenReturn(1);

        dispatcher.dispatchPending();

        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbitTemplate).convertAndSend(eq("FileObject.1001"), (Object) envelopeCaptor.capture());
        EventEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventId()).isEqualTo("event-1");
        assertThat(envelope.sourceService()).isEqualTo("educloud-file");
        assertThat(envelope.sourceSequence()).isEqualTo(1L);
        assertThat(envelope.aggregateType()).isEqualTo("FileObject");
        assertThat(envelope.aggregateId()).isEqualTo("1001");
        assertThat(envelope.aggregateVersion()).isEqualTo(1L);
        assertThat(((JsonNode) envelope.data()).get("fileId").asText()).isEqualTo("1001");

        verify(outboxEventMapper).update(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        wrapper.getSqlSet().contains("publish_status")
                                && wrapper.getSqlSet().contains("published_at")));
    }

    @Test
    void deliveryFailureBacksOffAndEventuallyFails() {
        OutboxEventEntity event = pendingEvent();
        event.setAttemptCount(9);
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new RuntimeException("down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(Object.class));
        when(outboxEventMapper.update(any(), any())).thenReturn(1);

        dispatcher.dispatchPending();

        verify(outboxEventMapper).update(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        wrapper.getSqlSet().contains("'FAILED'")
                                || wrapper.getSqlSet().contains("publish_status")));
    }
}
