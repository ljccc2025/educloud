package com.educloud.search.messaging;

import com.educloud.search.config.RabbitMqConfig;
import com.educloud.search.service.DlqRecoveryService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 搜索索引同步死信队列消费者
 * 监听队列 search.sync.dlq：收到死信后记录 ERROR 日志（含原始消息、失败原因、重试次数 header），
 * 并将死信落库为 index_sync_failure PENDING 记录，供定时/手动重放。
 * 始终 ACK 防止死信消息无限循环（落库失败仅告警，不重投）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDlqConsumer {

    private final DlqRecoveryService dlqRecoveryService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_SEARCH_DLQ)
    public void onDlqMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message != null && message.getMessageProperties() != null
                ? message.getMessageProperties().getDeliveryTag() : 0L;
        if (message == null) {
            return;
        }
        try {
            // ERROR 告警日志：原始消息 + 失败原因 + 重试次数（x-death header）
            MessageProperties props = message.getMessageProperties();
            String payloadPreview = previewPayload(message.getBody());
            int deathCount = extractDeathCount(props);
            String exchange = extractExchange(props);
            String routingKey = extractRoutingKey(props);
            log.error("Received dead-letter message on search DLQ: originalExchange={}, originalRoutingKey={}, "
                            + "deathCount={}, payload={}",
                    exchange, routingKey, deathCount, payloadPreview);

            dlqRecoveryService.recordFailure(message);
        } catch (Exception e) {
            // 落库失败也必须 ACK，避免死信队列无限循环；仅记录 ERROR 告警
            log.error("Failed to persist dead-letter message (deliveryTag={}), acking anyway: {}", deliveryTag, e.getMessage(), e);
        }
        if (channel != null) {
            channel.basicAck(deliveryTag, false);
        }
    }

    private int extractDeathCount(MessageProperties props) {
        if (props == null) {
            return 0;
        }
        Object xDeath = props.getHeader("x-death");
        if (xDeath instanceof List<?> deaths && !deaths.isEmpty() && deaths.get(deaths.size() - 1) instanceof Map<?, ?> death) {
            if (death.get("count") instanceof Number count) {
                return count.intValue();
            }
        }
        return 0;
    }

    private String extractExchange(MessageProperties props) {
        if (props == null) {
            return "";
        }
        Object xDeath = props.getHeader("x-death");
        if (xDeath instanceof List<?> deaths && !deaths.isEmpty() && deaths.get(deaths.size() - 1) instanceof Map<?, ?> death) {
            return death.get("exchange") != null ? String.valueOf(death.get("exchange")) : "";
        }
        return "";
    }

    private String extractRoutingKey(MessageProperties props) {
        if (props == null) {
            return "";
        }
        Object xDeath = props.getHeader("x-death");
        if (xDeath instanceof List<?> deaths && !deaths.isEmpty() && deaths.get(deaths.size() - 1) instanceof Map<?, ?> death) {
            Object routingKeys = death.get("routing-keys");
            if (routingKeys instanceof List<?> rkList && !rkList.isEmpty()) {
                return String.valueOf(rkList.get(0));
            }
        }
        return props.getReceivedRoutingKey() != null ? props.getReceivedRoutingKey() : "";
    }

    private String previewPayload(byte[] body) {
        if (body == null) {
            return "null";
        }
        String text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        return text.length() > 500 ? text.substring(0, 500) + "...(truncated)" : text;
    }
}
