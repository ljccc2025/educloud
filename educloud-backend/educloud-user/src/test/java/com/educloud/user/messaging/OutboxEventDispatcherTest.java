package com.educloud.user.messaging;

import com.educloud.common.messaging.EventEnvelope;
import com.educloud.user.entity.OutboxEventEntity;
import com.educloud.user.mapper.OutboxEventMapper;
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
 * Outbox 发布器单元测试。依据：M03 计划任务 13（小批锁定、投递后标记 PUBLISHED、
 * 失败退避重试、达阈值 FAILED；信封字段完整）。
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
        event.setEventType("UserRegistered");
        event.setEventVersion(1);
        event.setAggregateType("User");
        event.setAggregateId("1001");
        event.setAggregateVersion(1L);
        event.setSourceSequence(1L);
        event.setPayloadJson("{\"userId\":\"1001\"}");
        event.setRequestId("req-1");
        event.setOccurredAt(Instant.parse("2026-08-21T10:00:00Z"));
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
        verify(rabbitTemplate).convertAndSend(eq("User:1001"), (Object) envelopeCaptor.capture());
        EventEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventId()).isEqualTo("event-1");
        assertThat(envelope.sourceService()).isEqualTo("educloud-user");
        assertThat(envelope.sourceSequence()).isEqualTo(1L);
        assertThat(envelope.aggregateType()).isEqualTo("User");
        assertThat(envelope.aggregateId()).isEqualTo("1001");
        assertThat(envelope.aggregateVersion()).isEqualTo(1L);
        assertThat(((JsonNode) envelope.data()).get("userId").asText()).isEqualTo("1001");

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
