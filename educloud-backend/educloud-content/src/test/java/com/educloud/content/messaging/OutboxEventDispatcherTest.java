package com.educloud.content.messaging;

import com.educloud.content.entity.OutboxEventEntity;
import com.educloud.content.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

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
 * content Outbox 投递器单测（角色化动态流阶段 2）：验证 CAS 认领闭环与事件路由——
 * 作业批改事件发布到全域总线 educloud.events（routing key assignment.graded，
 * analytics 动态流作业队列定向绑定）；其余内容域事件发布到内容交换机
 * educloud.content.events（动态流内容队列以 # 通配绑定）。
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

    private OutboxEventEntity claimedEvent(long id, String eventType, String aggregateType,
                                           String aggregateId, int attemptCount) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(id);
        event.setEventId("evt-" + id);
        event.setEventType(eventType);
        event.setEventVersion(1);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setAggregateVersion(1L);
        event.setSourceSequence(id);
        event.setPayloadJson("{\"studentId\":77}");
        event.setRequestId("req-1");
        event.setOccurredAt(LocalDateTime.of(2026, 8, 27, 10, 0, 0));
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
    void assignmentGradedRoutesToDomainEventBusWithExactRoutingKey() {
        OutboxEventEntity event = claimedEvent(1L, "AssignmentGraded", "Assignment", "asg-001", 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));

        dispatcher.dispatchPending();

        // 全域总线定向路由：与 analytics 动态流作业队列绑定（assignment.graded）完全一致。
        verify(rabbitTemplate).convertAndSend(eq("educloud.events"), eq("assignment.graded"), any(Object.class));
        verify(outboxEventMapper).markPublished(event.getId());
    }

    @Test
    void assignmentSubmittedRoutesToContentExchange() {
        OutboxEventEntity event = claimedEvent(2L, "AssignmentSubmitted", "Assignment", "asg-001", 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));

        dispatcher.dispatchPending();

        verify(rabbitTemplate).convertAndSend(
                eq("educloud.content.events"), eq("assignment.submitted"), any(Object.class));
        verify(outboxEventMapper).markPublished(event.getId());
    }

    @Test
    void courseCompletedAndCertificateIssuedRouteToContentExchange() {
        OutboxEventEntity completed = claimedEvent(3L, "CourseCompleted", "LearningProgress", "1001", 0);
        OutboxEventEntity certificate = claimedEvent(4L, "CertificateIssued", "Certificate", "CERT-1", 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(2, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(completed, certificate));

        dispatcher.dispatchPending();

        verify(rabbitTemplate).convertAndSend(
                eq("educloud.content.events"), eq("course.completed"), any(Object.class));
        verify(rabbitTemplate).convertAndSend(
                eq("educloud.content.events"), eq("certificate.issued"), any(Object.class));
        verify(outboxEventMapper).markPublished(completed.getId());
        verify(outboxEventMapper).markPublished(certificate.getId());
    }

    @Test
    void unknownEventTypeFallsBackToAggregateRoutingKey() {
        OutboxEventEntity event = claimedEvent(5L, "ContentRevisionPublished", "CourseContent", "9001", 0);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));

        dispatcher.dispatchPending();

        verify(rabbitTemplate).convertAndSend(
                eq("educloud.content.events"), eq("content.revision.published"), any(Object.class));
        verify(outboxEventMapper).markPublished(event.getId());
    }

    @Test
    void relayRetriesClaimedEventWithBackoffAfterFailedDispatch() {
        OutboxEventEntity event = claimedEvent(6L, "AssignmentGraded", "Assignment", "asg-001", 2);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));
        doThrow(new AmqpException("RabbitMQ down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        LocalDateTime before = LocalDateTime.now();
        dispatcher.dispatchPending();

        // 退避公式：attempt=2 → 5s * 3 = 15s。
        verify(outboxEventMapper).markFailedAttempt(eq(event.getId()),
                argThat(nextAttemptAt -> nextAttemptAt.isAfter(before.plusSeconds(14))
                        && nextAttemptAt.isBefore(before.plusSeconds(16))));
        verify(outboxEventMapper, never()).markPublished(any());
        verify(outboxEventMapper, never()).markFailed(any(), any());
    }

    @Test
    void relayMarksFailedWhenAttemptsReachThreshold() {
        OutboxEventEntity event = claimedEvent(7L, "AssignmentGraded", "Assignment", "asg-001", 9);
        when(outboxEventMapper.claimPending(anyString(), anyInt(), anyInt())).thenReturn(1, 0);
        when(outboxEventMapper.selectClaimedByOwner(anyString())).thenReturn(List.of(event));
        doThrow(new AmqpException("RabbitMQ down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        LocalDateTime before = LocalDateTime.now();
        dispatcher.dispatchPending();

        // attempt 9 -> 10 >= MAX_ATTEMPTS(10)，标记 FAILED。
        verify(outboxEventMapper).markFailed(eq(event.getId()),
                argThat(nextAttemptAt -> nextAttemptAt.isAfter(before.plusSeconds(49))
                        && nextAttemptAt.isBefore(before.plusSeconds(51))));
        verify(outboxEventMapper, never()).markFailedAttempt(any(), any());
        verify(outboxEventMapper, never()).markPublished(any());
    }

    @Test
    void relayStopsAfterMaxBatchesToAvoidInfiniteLoop() {
        OutboxEventEntity event = claimedEvent(8L, "AssignmentSubmitted", "Assignment", "asg-001", 0);
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
