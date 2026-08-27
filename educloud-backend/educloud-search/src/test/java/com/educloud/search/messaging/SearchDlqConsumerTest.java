package com.educloud.search.messaging;

import com.educloud.search.service.DlqRecoveryService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchDlqConsumerTest {

    @Mock
    private DlqRecoveryService dlqRecoveryService;

    @Mock
    private Channel channel;

    @InjectMocks
    private SearchDlqConsumer searchDlqConsumer;

    private Message buildDeadLetterMessage() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(77L);
        properties.setHeader("x-death", List.of(Map.of(
                "exchange", "educloud.course.events",
                "routing-keys", List.of("course.published"),
                "count", 1,
                "reason", "rejected"
        )));
        String payload = "{\"messageId\":\"msg_dlq_001\",\"eventType\":\"CoursePublished\","
                + "\"aggregateType\":\"Course\",\"aggregateId\":\"1001\",\"aggregateVersion\":1}";
        return new Message(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), properties);
    }

    @Test
    @DisplayName("测试 DLQ 死信消息：记录 ERROR 日志并落库后 ACK")
    void testOnDlqMessage_RecordsFailureAndAcks() throws IOException {
        Message message = buildDeadLetterMessage();

        searchDlqConsumer.onDlqMessage(message, channel);

        verify(dlqRecoveryService, times(1)).recordFailure(message);
        verify(channel, times(1)).basicAck(77L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("测试 DLQ 落库异常时仍然 ACK（防止死信无限循环）")
    void testOnDlqMessage_RecordFailureThrows_StillAcks() throws IOException {
        Message message = buildDeadLetterMessage();
        doThrow(new RuntimeException("DB unavailable"))
                .when(dlqRecoveryService).recordFailure(message);

        searchDlqConsumer.onDlqMessage(message, channel);

        verify(dlqRecoveryService, times(1)).recordFailure(message);
        verify(channel, times(1)).basicAck(77L, false);
    }

    @Test
    @DisplayName("测试 null 消息防御性处理")
    void testOnDlqMessage_NullMessage_Safe() throws IOException {
        searchDlqConsumer.onDlqMessage(null, channel);
        verifyNoInteractions(dlqRecoveryService);
    }
}
