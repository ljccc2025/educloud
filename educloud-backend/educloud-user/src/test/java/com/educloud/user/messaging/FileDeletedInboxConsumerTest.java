package com.educloud.user.messaging;

import com.educloud.user.entity.InboxEventEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.educloud.user.mapper.InboxEventMapper;
import com.educloud.user.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileDeleted Inbox 消费者单元测试。依据：M04 计划任务 14（轮询 PENDING FileDeleted、
 * 匹配 avatar_file_id 置空、PROCESSED/AVATAR_CLEARED、失败退避计数、达阈值 FAILED、
 * 已 PROCESSED 跳过、不匹配 NO_OP）。
 */
@ExtendWith(MockitoExtension.class)
class FileDeletedInboxConsumerTest {

    @Mock
    private InboxEventMapper inboxEventMapper;
    @Mock
    private UserProfileMapper userProfileMapper;

    private FileDeletedInboxConsumer consumer;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-22T10:00:00Z"));
        consumer = new FileDeletedInboxConsumer(inboxEventMapper, userProfileMapper, clock);
    }

    private InboxEventEntity pendingEvent(Long id, Long fileId) {
        InboxEventEntity event = new InboxEventEntity();
        event.setId(id);
        event.setEventId("evt-" + id);
        event.setEventType("FileDeleted");
        // inbox_event 无 payload 列：FileDeleted 信封约定 aggregateId=fileId。
        event.setAggregateId(String.valueOf(fileId));
        event.setProcessStatus("PENDING");
        return event;
    }

    @Test
    void matchingFileIdClearsAvatarAndMarksProcessed() {
        when(inboxEventMapper.selectList(any())).thenReturn(List.of(pendingEvent(1L, 9001L)));
        when(userProfileMapper.update(isNull(), any())).thenReturn(1);
        when(inboxEventMapper.update(isNull(), any())).thenReturn(1);

        consumer.consumePending();

        verify(userProfileMapper).update(
                isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        wrapper.getSqlSet().contains("avatar_file_id")));
        verify(inboxEventMapper).update(
                isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("PROCESSED")
                                && ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("AVATAR_CLEARED")));
    }

    @Test
    void nonMatchingFileIdIsProcessedAsNoOp() {
        when(inboxEventMapper.selectList(any())).thenReturn(List.of(pendingEvent(1L, 9001L)));
        when(userProfileMapper.update(isNull(), any())).thenReturn(0);
        when(inboxEventMapper.update(isNull(), any())).thenReturn(1);

        consumer.consumePending();

        verify(userProfileMapper).update(isNull(), any());
        verify(inboxEventMapper).update(
                isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("PROCESSED")
                                && ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("NO_OP")));
    }

    @Test
    void failureBacksOffAndMarksFailedAfterRetryLimit() {
        InboxEventEntity event = pendingEvent(1L, 9001L);
        when(inboxEventMapper.selectList(any())).thenReturn(List.of(event));
        when(userProfileMapper.update(isNull(), any()))
                .thenThrow(new RuntimeException("profile db down"));
        when(inboxEventMapper.update(isNull(), any())).thenReturn(1);

        for (int attempt = 0; attempt < 4; attempt++) {
            consumer.consumePending();
            clock.advance(Duration.ofSeconds(10L));
        }
        consumer.consumePending();

        verify(inboxEventMapper, times(4)).update(
                isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("PENDING")));
        verify(inboxEventMapper).update(
                isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("FAILED")));
    }

    @Test
    void backoffSkipsEventBeforeNextAttemptIsDue() {
        InboxEventEntity event = pendingEvent(1L, 9001L);
        when(inboxEventMapper.selectList(any())).thenReturn(List.of(event));
        when(userProfileMapper.update(isNull(), any()))
                .thenThrow(new RuntimeException("profile db down"));
        when(inboxEventMapper.update(isNull(), any())).thenReturn(1);

        consumer.consumePending();
        // 同一时钟时刻再次轮询：退避期内跳过，不重复记账。
        consumer.consumePending();

        verify(inboxEventMapper, times(1)).update(isNull(), any());
    }


    @Test
    void failedEventIsRemovedFromBackoffMapAndRetriesImmediately() {
        InboxEventEntity event = pendingEvent(1L, 9001L);
        when(inboxEventMapper.selectList(any())).thenReturn(List.of(event));
        when(userProfileMapper.update(isNull(), any()))
                .thenThrow(new RuntimeException("profile db down"));
        when(inboxEventMapper.update(isNull(), any())).thenReturn(1);

        for (int attempt = 0; attempt < 4; attempt++) {
            consumer.consumePending();
            clock.advance(Duration.ofSeconds(10L));
        }
        // 第 5 次达阈值置 FAILED；B3 修复后退避条目应被移除。
        consumer.consumePending();

        // 条目已清理：同一时刻再次轮询不再被退避跳过，立即重试（重新从第 1 次尝试计数）。
        consumer.consumePending();

        verify(inboxEventMapper, times(5)).update(
                isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("PENDING")));
        verify(inboxEventMapper, times(1)).update(
                isNull(),
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        ((UpdateWrapper<InboxEventEntity>) wrapper).getParamNameValuePairs().containsValue("FAILED")));
        verify(inboxEventMapper, times(6)).update(isNull(), any());
    }

    @Test
    void processedEventsAreSkippedByIdempotentQuery() {
        InboxEventEntity event = pendingEvent(1L, 9001L);
        when(inboxEventMapper.selectList(any()))
                .thenReturn(List.of(event), List.of());
        when(userProfileMapper.update(isNull(), any())).thenReturn(1);
        when(inboxEventMapper.update(isNull(), any())).thenReturn(1);

        consumer.consumePending();
        consumer.consumePending();

        verify(userProfileMapper, times(1)).update(isNull(), any());
    }

    @Test
    void pollOnlySelectsPendingFileDeletedEvents() {
        when(inboxEventMapper.selectList(any())).thenReturn(List.of());

        consumer.consumePending();

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InboxEventEntity>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(inboxEventMapper).selectList(captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment())
                .contains("event_type")
                .contains("process_status");
    }

    /** 可推进的测试时钟。 */
    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
