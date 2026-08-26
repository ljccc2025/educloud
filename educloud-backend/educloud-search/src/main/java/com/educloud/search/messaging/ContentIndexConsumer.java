package com.educloud.search.messaging;

import com.educloud.search.config.RabbitMqConfig;
import com.educloud.search.messaging.event.ContentDomainEvent;
import com.educloud.search.service.IndexSyncService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 章节与课件领域事件消费者
 * 监听队列 search.content.sync.queue，执行实时课件列表增量索引更新与手动 ACK/NACK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentIndexConsumer {

    private final IndexSyncService indexSyncService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CONTENT_SYNC)
    public void onMessage(ContentDomainEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message != null && message.getMessageProperties() != null
                ? message.getMessageProperties().getDeliveryTag() : 0L;
        try {
            log.info("Received ContentDomainEvent: messageId={}, eventType={}, aggregateId={}",
                    event != null ? event.getEffectiveMessageId() : "null",
                    event != null ? event.getEventType() : "null",
                    event != null ? event.getAggregateId() : "null");

            if (event != null) {
                indexSyncService.handleContentEvent(event);
            }
            if (channel != null) {
                channel.basicAck(deliveryTag, false);
            }
            log.debug("Successfully ACKed ContentDomainEvent deliveryTag={}", deliveryTag);
        } catch (Exception e) {
            log.error("Failed to process ContentDomainEvent (deliveryTag={}): {}", deliveryTag, e.getMessage(), e);
            if (channel != null) {
                // 手动 NACK 不重回原队列（requeue=false），触发消息路由到死信队列 search.sync.dlq
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
}
