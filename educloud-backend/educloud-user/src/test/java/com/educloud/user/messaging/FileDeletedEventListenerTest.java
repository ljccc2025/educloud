package com.educloud.user.messaging;

import com.educloud.common.messaging.EventEnvelope;
import com.educloud.user.entity.InboxEventEntity;
import com.educloud.user.mapper.InboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileDeletedEventListener 单元测试（B1 修复）。直接调用 listener 方法验证：
 * 合法 FileDeleted 事件幂等写入 inbox_event（PENDING、source_service=educloud-file、
 * 信封字段对齐 InboxEventEntity）；重复 eventId 跳过；非 FileDeleted 忽略；
 * uk_inbox_event_id 竞态（DuplicateKeyException）兜底；Jackson 转换器可还原
 * EventEnvelope&lt;JsonNode&gt; 泛型信封。
 */
@ExtendWith(MockitoExtension.class)
class FileDeletedEventListenerTest {

    @Mock
    private InboxEventMapper inboxEventMapper;

    private FileDeletedEventListener listener;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-22T10:00:00Z"));
        listener = new FileDeletedEventListener(inboxEventMapper, clock);
    }

    private EventEnvelope<JsonNode> fileDeleted(String eventId, long sequence) {
        return new EventEnvelope<>(
                eventId,
                "FileDeleted",
                1,
                "educloud-file",
                sequence,
                "FileObject",
                "9001",
                4L,
                Instant.parse("2026-08-22T09:59:00Z"),
                "req-1",
                "trace-1",
                new ObjectMapper().createObjectNode().put("fileId", 9001L));
    }

    @Test
    void validFileDeletedEventWritesPendingInboxRow() {
        when(inboxEventMapper.selectCount(any())).thenReturn(0L);
        when(inboxEventMapper.insert(any(InboxEventEntity.class))).thenReturn(1);

        listener.onFileDeleted(fileDeleted("evt-1", 7L));

        ArgumentCaptor<InboxEventEntity> captor = ArgumentCaptor.forClass(InboxEventEntity.class);
        verify(inboxEventMapper).insert(captor.capture());
        InboxEventEntity event = captor.getValue();
        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getEventType()).isEqualTo("FileDeleted");
        assertThat(event.getSourceService()).isEqualTo("educloud-file");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getSourceSequence()).isEqualTo(7L);
        assertThat(event.getAggregateType()).isEqualTo("FileObject");
        assertThat(event.getAggregateId()).isEqualTo("9001");
        assertThat(event.getAggregateVersion()).isEqualTo(4L);
        assertThat(event.getProcessStatus()).isEqualTo("PENDING");
        assertThat(event.getReceivedAt()).isEqualTo(clock.instant());
        assertThat(event.getBusinessEffect()).isNull();
        assertThat(event.getProcessedAt()).isNull();
        assertThat(event.getErrorCode()).isNull();
    }

    @Test
    void duplicateEventIdIsSkippedBeforeInsert() {
        when(inboxEventMapper.selectCount(any())).thenReturn(1L);

        listener.onFileDeleted(fileDeleted("evt-1", 7L));

        verify(inboxEventMapper, never()).insert(any(InboxEventEntity.class));
    }

    @Test
    void nonFileDeletedEventIsIgnored() {
        EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                "evt-2",
                "FileUploaded",
                1,
                "educloud-file",
                8L,
                "FileObject",
                "9002",
                1L,
                Instant.parse("2026-08-22T09:59:00Z"),
                "req-2",
                "trace-2",
                new ObjectMapper().createObjectNode().put("fileId", 9002L));

        listener.onFileDeleted(envelope);

        verify(inboxEventMapper, never()).selectCount(any());
        verify(inboxEventMapper, never()).insert(any(InboxEventEntity.class));
    }

    @Test
    void duplicateKeyRaceIsSwallowed() {
        when(inboxEventMapper.selectCount(any())).thenReturn(0L);
        when(inboxEventMapper.insert(any(InboxEventEntity.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatCode(() -> listener.onFileDeleted(fileDeleted("evt-1", 7L)))
                .doesNotThrowAnyException();
        verify(inboxEventMapper).insert(any(InboxEventEntity.class));
    }

    @Test
    void jacksonConverterRoundTripsEnvelopeFieldsThroughFrameworkConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        EventEnvelope<JsonNode> envelope = fileDeleted("evt-rt", 9L);
        Message message = converter.toMessage(envelope, new MessageProperties());

        // @RabbitListener 经 MessagingMessageConverter 无方法参数类型提示调用 fromMessage；
        // 标准 Jackson2JsonMessageConverter 不保留记录（record）泛型信息，data 以 Object
        // 擦除为 LinkedHashMap（见 B1「注意泛型 JsonNode」）。监听器因此只读取信封元数据
        // 字段（eventId/eventType/aggregateId 等），不依赖 data 的具体类型。
        Object deserialized = converter.fromMessage(message);

        assertThat(deserialized).isInstanceOf(EventEnvelope.class);
        EventEnvelope<?> roundTrip = (EventEnvelope<?>) deserialized;
        assertThat(roundTrip.eventType()).isEqualTo("FileDeleted");
        assertThat(roundTrip.sourceService()).isEqualTo("educloud-file");
        assertThat(roundTrip.aggregateType()).isEqualTo("FileObject");
        assertThat(roundTrip.aggregateId()).isEqualTo("9001");
        assertThat(roundTrip.sourceSequence()).isEqualTo(9L);
        assertThat(roundTrip.data()).isInstanceOf(java.util.Map.class);
    }

    /** 可推进的测试时钟。 */
    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
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
