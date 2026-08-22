package com.educloud.user.messaging;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.common.messaging.EventEnvelope;
import com.educloud.user.entity.InboxEventEntity;
import com.educloud.user.mapper.InboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * FileDeleted 事件入 inbox 监听器（B1 修复，M04 任务 14 链路打通）。
 *
 * <p>从 educloud.user.inbox.filedeleted 队列接收 File 侧发布的
 * {@link EventEnvelope}（Jackson 反序列化，参数声明 data 泛型为 {@link JsonNode}；
 * 注意标准 Jackson2JsonMessageConverter 不保留 record 泛型，运行时 data 以 Object
 * 擦除为 LinkedHashMap，故本监听器只读取信封元数据字段、不依赖 data），
 * 校验 eventType=FileDeleted 后幂等写入 inbox_event：process_status=PENDING、
 * source_service='educloud-file'、信封字段对齐 {@link InboxEventEntity}；
 * uk_inbox_event_id 防重 —— 收到重复 eventId 时跳过（先查后插，并兜底捕获
 * {@link DuplicateKeyException} 处理并发竞态）。非 FileDeleted 事件忽略。
 *
 * <p>inbox_event 无 payload 列：后续 {@link FileDeletedInboxConsumer} 以
 * aggregateId=fileId 约定清理 avatar 引用。</p>
 */
@Component
public class FileDeletedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDeletedEventListener.class);
    private static final String FILE_DELETED = "FileDeleted";
    private static final String SOURCE_SERVICE = "educloud-file";
    private static final String STATUS_PENDING = "PENDING";

    private final InboxEventMapper inboxEventMapper;
    private final Clock clock;

    public FileDeletedEventListener(InboxEventMapper inboxEventMapper, Clock clock) {
        this.inboxEventMapper = Objects.requireNonNull(inboxEventMapper, "inboxEventMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @RabbitListener(queues = "educloud.user.inbox.filedeleted")
    public void onFileDeleted(EventEnvelope<JsonNode> envelope) {
        if (envelope == null || !FILE_DELETED.equals(envelope.eventType())) {
            LOGGER.info("忽略非 FileDeleted 事件: eventType={}", envelope == null ? null : envelope.eventType());
            return;
        }
        if (inboxEventMapper.selectCount(
                new QueryWrapper<InboxEventEntity>().eq("event_id", envelope.eventId())) > 0) {
            LOGGER.info("重复 FileDeleted 事件跳过: eventId={}", envelope.eventId());
            return;
        }
        Instant now = clock.instant();
        InboxEventEntity event = new InboxEventEntity();
        event.setEventId(envelope.eventId());
        event.setEventType(envelope.eventType());
        event.setSourceService(SOURCE_SERVICE);
        event.setEventVersion(envelope.eventVersion());
        event.setSourceSequence(envelope.sourceSequence());
        event.setAggregateType(envelope.aggregateType());
        event.setAggregateId(envelope.aggregateId());
        event.setAggregateVersion(envelope.aggregateVersion());
        event.setProcessStatus(STATUS_PENDING);
        event.setReceivedAt(now);
        try {
            inboxEventMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            LOGGER.info("uk_inbox_event_id 防重兜底，重复 FileDeleted 事件跳过: eventId={}", envelope.eventId());
        }
    }
}
