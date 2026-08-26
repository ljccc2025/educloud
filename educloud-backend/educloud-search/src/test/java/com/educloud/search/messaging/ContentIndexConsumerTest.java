package com.educloud.search.messaging;

import com.educloud.search.messaging.event.ContentDomainEvent;
import com.educloud.search.service.IndexSyncService;
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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentIndexConsumerTest {

    @Mock
    private IndexSyncService indexSyncService;

    @Mock
    private Channel channel;

    @InjectMocks
    private ContentIndexConsumer contentIndexConsumer;

    @Test
    @DisplayName("测试课件消息成功消费并触发 Channel 手动 basicAck")
    void testOnMessage_Success_AcksMessage() throws IOException {
        ContentDomainEvent event = ContentDomainEvent.builder()
                .messageId("msg_cnt_001")
                .eventType("LessonPublished")
                .aggregateId("7001")
                .aggregateVersion(1L)
                .build();

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(55L);
        Message message = new Message("{}".getBytes(), properties);

        contentIndexConsumer.onMessage(event, message, channel);

        verify(indexSyncService, times(1)).handleContentEvent(event);
        verify(channel, times(1)).basicAck(55L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("测试课件消息处理失败触发 Channel 手动 basicNack(requeue=false)")
    void testOnMessage_Failure_NacksMessageToDlq() throws IOException {
        ContentDomainEvent event = ContentDomainEvent.builder()
                .messageId("msg_cnt_err_002")
                .eventType("LessonPublished")
                .aggregateId("7001")
                .aggregateVersion(1L)
                .build();

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(77L);
        Message message = new Message("{}".getBytes(), properties);

        doThrow(new RuntimeException("Database error"))
                .when(indexSyncService).handleContentEvent(event);

        contentIndexConsumer.onMessage(event, message, channel);

        verify(indexSyncService, times(1)).handleContentEvent(event);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(77L, false, false);
    }
}
