package com.educloud.search.messaging;

import com.educloud.search.config.RabbitMqConfig;
import com.educloud.search.messaging.event.CourseDomainEvent;
import com.educloud.search.service.IndexSyncService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 课程领域事件消费者
 * 监听队列 search.course.sync.queue，执行实时增量索引更新与手动 ACK/NACK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseIndexConsumer {

    private final IndexSyncService indexSyncService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_COURSE_SYNC)
    public void onMessage(CourseDomainEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message != null && message.getMessageProperties() != null
                ? message.getMessageProperties().getDeliveryTag() : 0L;
        try {
            log.info("Received CourseDomainEvent: messageId={}, eventType={}, aggregateId={}",
                    event != null ? event.getEffectiveMessageId() : "null",
                    event != null ? event.getEventType() : "null",
                    event != null ? event.getAggregateId() : "null");

            if (event != null) {
                indexSyncService.handleCourseEvent(event);
            }
            if (channel != null) {
                channel.basicAck(deliveryTag, false);
            }
            log.debug("Successfully ACKed CourseDomainEvent deliveryTag={}", deliveryTag);
        } catch (Exception e) {
            log.error("Failed to process CourseDomainEvent (deliveryTag={}): {}", deliveryTag, e.getMessage(), e);
            if (channel != null) {
                // 手动 NACK 不重回原队列（requeue=false），触发消息路由到死信队列 search.sync.dlq
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
}
