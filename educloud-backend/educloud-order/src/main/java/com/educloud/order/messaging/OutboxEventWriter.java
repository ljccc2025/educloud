package com.educloud.order.messaging;

import com.educloud.order.entity.OutboxEventEntity;
import com.educloud.order.mapper.OutboxEventMapper;
import com.educloud.order.mapper.OutboxSequenceMapper;
import com.educloud.order.messaging.dto.OrderPaidEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox 事件写入器（BUG-017 修复）：在业务事务内将 OrderPaid 事件落
 * outbox_event 表（与订单状态 CAS 同事务提交），由 {@link OutboxRelay}
 * 在事务提交后异步投递 MQ——消除「事务提交前 MQ 发送失败事件永久丢失」
 * 与「事务回滚后幻影已支付事件」两类交易/履约不一致。
 *
 * <p>source_sequence 经 outbox_sequence 行锁串行推进（REPEATABLE READ 下
 * 同事务可见自身更新），保证投递按全局顺序；写入失败抛异常令业务事务
 * 整体回滚（fail-closed，不产生无事件的成功支付）。</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private static final String SOURCE_NAME = "educloud-order";
    private static final String AGGREGATE_TYPE = "TRADE_ORDER";
    private static final String EVENT_TYPE_ORDER_PAID = "ORDER_PAID";

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxSequenceMapper outboxSequenceMapper;
    private final ObjectMapper objectMapper;

    /** 与订单置 PAID 同事务写入（aggregateVersion 为 CAS 递增后的聚合版本）。 */
    public void appendOrderPaid(OrderPaidEvent event, long aggregateVersion) {
        outboxSequenceMapper.increment(SOURCE_NAME);
        Long sequence = outboxSequenceMapper.selectValue(SOURCE_NAME);
        if (sequence == null) {
            throw new IllegalStateException("Outbox sequence not initialized: " + SOURCE_NAME);
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Failed to serialize OrderPaidEvent: " + event.getOrderId(), failure);
        }

        OutboxEventEntity entity = OutboxEventEntity.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(String.valueOf(event.getOrderId()))
                .eventType(EVENT_TYPE_ORDER_PAID)
                .eventVersion(1)
                .aggregateVersion(aggregateVersion)
                .payloadJson(payloadJson)
                .requestId(UUID.randomUUID().toString())
                .occurredAt(event.getPaidAt() != null ? event.getPaidAt() : LocalDateTime.now())
                .sourceSequence(sequence)
                .publishStatus("PENDING")
                .attemptCount(0)
                .build();
        outboxEventMapper.insert(entity);
    }
}
