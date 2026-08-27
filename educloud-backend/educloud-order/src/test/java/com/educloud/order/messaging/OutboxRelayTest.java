package com.educloud.order.messaging;

import com.educloud.order.entity.OutboxEventEntity;
import com.educloud.order.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;

import java.time.LocalDateTime;
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
 * OutboxRelay 单测（BUG-042/058/064 修复后）：mock Mapper 验证 CAS 认领闭环——
 * 先回置陈旧认领 → 批量认领 → 仅投递本实例认领的事件 → 成功 markPublished /
 * 失败 markFailedAttempt（attempt+1 由 SQL 原子完成，此处验证调用与退避时间）。
 */
class OutboxRelayTest {

    private OutboxEventMapper outboxEventMapper;
    private OrderEventPublisher orderEventPublisher;
    private ObjectMapper objectMapper;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        outboxEventMapper = mock(OutboxEventMapper.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        relay = new OutboxRelay(outboxEventMapper, orderEventPublisher, objectMapper);
    }

    private OutboxEventEntity claimedEvent(long id, int attemptCount) {
        return OutboxEventEntity.builder()
                .id(id)
                .eventId("evt-" + id)
                .aggregateId("1001")
                .eventType("ORDER_PAID")
                .attemptCount(attemptCount)
                .sourceSequence(id)
                .payloadJson("{\"orderId\":\"1001\",\"orderNo\":\"ORD1001\",\"studentId\":\"7\","
                        + "\"courseIds\":[3,4],\"paidAmount\":99.00,\"paidAt\":\"2026-08-27T10:00:00\"}")
                .build();
    }

    @Test
    void relayReleasesStaleClaimsBeforeClaiming() {
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(0);

        relay.relayPendingEvents();

        verify(outboxEventMapper).releaseStaleClaims(300);
        verify(outboxEventMapper).claimPending(anyString(), eq(10), eq(50));
        verify(outboxEventMapper, never()).selectClaimedByOwner(anyString());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void relayStopsWhenNoPendingEvents() {
        when(outboxEventMapper.releaseStaleClaims(anyInt())).thenReturn(0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(0);

        relay.relayPendingEvents();

        verify(outboxEventMapper, times(1)).claimPending(anyString(), anyInt(), anyInt());
        verify(outboxEventMapper, never()).selectClaimedByOwner(anyString());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void relayPublishesClaimedEventAfterSuccessfulDispatch() {
        OutboxEventEntity event = claimedEvent(1L, 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));

        relay.relayPendingEvents();

        verify(outboxEventMapper).markPublished(event.getId());
        verify(orderEventPublisher).publishOrderPaid(argThat(payload -> payload.getOrderId() == 1001L));
        verify(outboxEventMapper, never()).markFailedAttempt(any(), any());
    }

    @Test
    void relayRetriesClaimedEventWithBackoffAfterFailedDispatch() {
        OutboxEventEntity event = claimedEvent(2L, 2);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));
        doThrow(new AmqpException("RabbitMQ down"))
                .when(orderEventPublisher)
                .publishOrderPaid(any());

        LocalDateTime before = LocalDateTime.now();
        relay.relayPendingEvents();

        // 退避公式不变：attempt=2 → 5s * 2^2 = 20s。
        verify(outboxEventMapper).markFailedAttempt(eq(event.getId()),
                argThat(nextAttemptAt -> nextAttemptAt.isAfter(before.plusSeconds(19))
                        && nextAttemptAt.isBefore(before.plusSeconds(21))));
        verify(outboxEventMapper, never()).markPublished(any());
    }

    @Test
    void relayStopsAfterMaxBatchesToAvoidInfiniteLoop() {
        OutboxEventEntity event = claimedEvent(3L, 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));

        relay.relayPendingEvents();

        // 每轮认领 1 条且均成功：循环 10 批（MAX_BATCHES）后停止，不会无限循环。
        verify(outboxEventMapper, times(10)).claimPending(anyString(), eq(10), eq(50));
        verify(outboxEventMapper, times(10)).markPublished(any());
        verify(outboxEventMapper, times(1)).releaseStaleClaims(300);
    }

    @Test
    void relaySkipsDispatchWhenClaimedBatchVanished() {
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of());

        relay.relayPendingEvents();

        verify(outboxEventMapper, times(1)).claimPending(anyString(), anyInt(), anyInt());
        verifyNoInteractions(orderEventPublisher);
    }
}
