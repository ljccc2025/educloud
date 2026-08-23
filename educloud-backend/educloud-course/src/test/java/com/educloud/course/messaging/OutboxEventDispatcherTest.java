package com.educloud.course.messaging;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.educloud.common.messaging.EventEnvelope;
import com.educloud.course.entity.OutboxEventEntity;
import com.educloud.course.mapper.OutboxEventMapper;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 15：Outbox 发布器单元测试（mock OutboxEventMapper + RabbitTemplate）。
 *
 * <p>依据：M03/M04 发布器模式（PENDING 小批锁定、投递后标记 PUBLISHED、失败退避重试、
 * 达阈值 FAILED；信封字段完整）。M04 坑 3：交换机为 Topic，routing key 用点分隔
 * {@code aggregateType.aggregateId}（Course.10001），可被 Course.# 通配绑定命中；
 * 并发安全：状态更新 WHERE id=? AND publish_status='PENDING'（任务 15 要求，
 * 经 UpdateWrapper eq 条件进入 paramNameValuePairs）。</p>
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
        event.setEventType("CoursePublished");
        event.setEventVersion(1);
        event.setAggregateType("Course");
        event.setAggregateId("1001");
        event.setAggregateVersion(1L);
        event.setSourceSequence(1L);
        event.setPayloadJson("{\"courseId\":1001}");
        event.setRequestId("req-1");
        event.setOccurredAt(Instant.parse("2026-08-23T10:00:00Z"));
        event.setPublishStatus("PENDING");
        event.setAttemptCount(0);
        return event;
    }

    @Test
    void publishesEnvelopeWithDotRoutingKeyAndMarksPublished() {
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(pendingEvent()));
        when(outboxEventMapper.update(any(), any())).thenReturn(1);

        dispatcher.dispatchPending();

        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbitTemplate).convertAndSend(eq("Course.1001"), (Object) envelopeCaptor.capture());
        EventEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventId()).isEqualTo("event-1");
        assertThat(envelope.eventType()).isEqualTo("CoursePublished");
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.sourceService()).isEqualTo("educloud-course");
        assertThat(envelope.sourceSequence()).isEqualTo(1L);
        assertThat(envelope.aggregateType()).isEqualTo("Course");
        assertThat(envelope.aggregateId()).isEqualTo("1001");
        assertThat(envelope.aggregateVersion()).isEqualTo(1L);
        assertThat(envelope.requestId()).isEqualTo("req-1");
        assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-08-23T10:00:00Z"));
        assertThat(((JsonNode) envelope.data()).get("courseId").asLong()).isEqualTo(1001L);

        UpdateWrapper<OutboxEventEntity> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("publish_status").contains("published_at");
        // 并发安全：仅当仍为 PENDING 才推进为 PUBLISHED（eq 条件在 WHERE 段）。
        assertThat(update.getSqlSegment()).contains("publish_status");
        assertThat(update.getParamNameValuePairs()).containsValue("PENDING").containsValue("PUBLISHED");
    }

    @Test
    void deliveryFailureBacksOffAndKeepsPending() {
        OutboxEventEntity event = pendingEvent();
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(event));
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(Object.class));
        when(outboxEventMapper.update(any(), any())).thenReturn(1);

        dispatcher.dispatchPending();

        UpdateWrapper<OutboxEventEntity> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("attempt_count").contains("next_attempt_at");
        assertThat(update.getParamNameValuePairs())
                .containsValue(1)
                .containsValue("PENDING");
        // 退避：next_attempt_at 被排程为未来时刻（5s * 1 次尝试）。
        assertThat(update.getParamNameValuePairs().values())
                .anyMatch(value -> value instanceof Instant);
    }

    @Test
    void deliveryFailureReachesThresholdAndMarksFailed() {
        OutboxEventEntity event = pendingEvent();
        event.setAttemptCount(9);
        when(outboxEventMapper.selectList(any())).thenReturn(List.of(event));
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(Object.class));
        when(outboxEventMapper.update(any(), any())).thenReturn(1);

        dispatcher.dispatchPending();

        UpdateWrapper<OutboxEventEntity> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("attempt_count").contains("publish_status");
        // 达阈值：attempt 9 -> 10 >= MAX_ATTEMPTS(10)，标记 FAILED。
        assertThat(update.getParamNameValuePairs()).containsValue(10).containsValue("FAILED");
    }

    @SuppressWarnings("unchecked")
    private UpdateWrapper<OutboxEventEntity> capturedUpdate() {
        ArgumentCaptor<Wrapper<OutboxEventEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(outboxEventMapper).update(isNull(), captor.capture());
        return (UpdateWrapper<OutboxEventEntity>) captor.getValue();
    }
}
