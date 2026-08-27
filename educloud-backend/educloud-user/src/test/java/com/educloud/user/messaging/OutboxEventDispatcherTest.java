package com.educloud.user.messaging;

import com.educloud.user.entity.OutboxEventEntity;
import com.educloud.user.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Outbox 投递器单测（BUG-042/058/064 修复后）：mock Mapper 验证 CAS 认领闭环——
 * 先回置陈旧认领 → 批量认领 → 仅投递本实例认领的事件 → 成功 markPublished /
 * 失败 markFailedAttempt（attempt+1 由 SQL 原子完成，此处验证调用与退避时间）/
 * 达阈值 markFailed（终态语义保持原有值）。
 */
class OutboxEventDispatcherTest {

    private OutboxEventMapper outboxEventMapper;
    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private OutboxEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        outboxEventMapper = mock(OutboxEventMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();
        dispatcher = new OutboxEventDispatcher(outboxEventMapper, rabbitTemplate, objectMapper);
    }

    private OutboxEventEntity claimedEvent(long id, int attemptCount) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(id);
        event.setEventId("evt-" + id);
        event.setEventType("UserRegistered");
        event.setEventVersion(1);
        event.setAggregateType("User");
        event.setAggregateId("1001");
        event.setAggregateVersion(1L);
        event.setSourceSequence(id);
        event.setPayloadJson("{\"userId\":\"1001\"}");
        event.setRequestId("req-1");
        event.setOccurredAt(Instant.parse("2026-08-21T10:00:00Z"));
        event.setPublishStatus("CLAIMED");
        event.setAttemptCount(attemptCount);
        return event;
    }

    @Test
    void relayReleasesStaleClaimsBeforeClaiming() {
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(0);

        dispatcher.dispatchPending();

        verify(outboxEventMapper).releaseStaleClaims(300);
        verify(outboxEventMapper).claimPending(anyString(), eq(10), eq(50));
        verify(outboxEventMapper, never()).selectClaimedByOwner(anyString());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void relayStopsWhenNoPendingEvents() {
        when(outboxEventMapper.releaseStaleClaims(anyInt())).thenReturn(0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(0);

        dispatcher.dispatchPending();

        verify(outboxEventMapper, times(1)).claimPending(anyString(), anyInt(), anyInt());
        verify(outboxEventMapper, never()).selectClaimedByOwner(anyString());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void relayPublishesClaimedEventAfterSuccessfulDispatch() {
        OutboxEventEntity event = claimedEvent(1L, 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));

        dispatcher.dispatchPending();

        verify(rabbitTemplate).convertAndSend(eq("User:1001"), any(Object.class));
        verify(outboxEventMapper).markPublished(event.getId());
        verify(outboxEventMapper, never()).markFailedAttempt(any(), any());
        verify(outboxEventMapper, never()).markFailed(any(), any());
    }

    @Test
    void relayRetriesClaimedEventWithBackoffAfterFailedDispatch() {
        OutboxEventEntity event = claimedEvent(2L, 2);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));
        doThrow(new AmqpException("RabbitMQ down"))
                .when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

        Instant before = Instant.now();
        dispatcher.dispatchPending();

        // 退避公式不变：attempt=2 → 5s * 3 = 15s。
        verify(outboxEventMapper).markFailedAttempt(eq(event.getId()),
                argThat(nextAttemptAt -> nextAttemptAt.isAfter(before.plusSeconds(14))
                        && nextAttemptAt.isBefore(before.plusSeconds(16))));
        verify(outboxEventMapper, never()).markPublished(any());
        verify(outboxEventMapper, never()).markFailed(any(), any());
    }

    @Test
    void relayMarksFailedWhenAttemptsReachThreshold() {
        OutboxEventEntity event = claimedEvent(3L, 9);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));
        doThrow(new AmqpException("RabbitMQ down"))
                .when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

        Instant before = Instant.now();
        dispatcher.dispatchPending();

        // 终态语义不变：attempt 9 -> 10 >= MAX_ATTEMPTS(10)，标记 FAILED（next_attempt_at 一并保留）。
        verify(outboxEventMapper).markFailed(eq(event.getId()),
                argThat(nextAttemptAt -> nextAttemptAt.isAfter(before.plusSeconds(49))
                        && nextAttemptAt.isBefore(before.plusSeconds(51))));
        verify(outboxEventMapper, never()).markFailedAttempt(any(), any());
        verify(outboxEventMapper, never()).markPublished(any());
    }

    @Test
    void relayStopsAfterMaxBatchesToAvoidInfiniteLoop() {
        OutboxEventEntity event = claimedEvent(4L, 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));

        dispatcher.dispatchPending();

        // 每轮认领 1 条且均成功：循环 10 批（MAX_BATCHES）后停止，不会无限循环。
        verify(outboxEventMapper, times(10)).claimPending(anyString(), eq(10), eq(50));
        verify(outboxEventMapper, times(10)).markPublished(any());
        verify(outboxEventMapper, times(1)).releaseStaleClaims(300);
    }

    @Test
    void relaySkipsDispatchWhenClaimedBatchVanished() {
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of());

        dispatcher.dispatchPending();

        verify(outboxEventMapper, times(1)).claimPending(anyString(), anyInt(), anyInt());
        verifyNoInteractions(rabbitTemplate);
    }
}
