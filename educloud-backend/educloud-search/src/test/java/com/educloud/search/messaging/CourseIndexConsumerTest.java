package com.educloud.search.messaging;

import com.educloud.search.messaging.event.CourseDomainEvent;
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
class CourseIndexConsumerTest {

    @Mock
    private IndexSyncService indexSyncService;

    @Mock
    private Channel channel;

    @InjectMocks
    private CourseIndexConsumer courseIndexConsumer;

    @Test
    @DisplayName("测试消息成功消费并触发 Channel 手动 basicAck")
    void testOnMessage_Success_AcksMessage() throws IOException {
        CourseDomainEvent event = CourseDomainEvent.builder()
                .messageId("msg_ack_001")
                .eventType("CoursePublished")
                .aggregateId("1001")
                .aggregateVersion(1L)
                .build();

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(42L);
        Message message = new Message("{}".getBytes(), properties);

        courseIndexConsumer.onMessage(event, message, channel);

        verify(indexSyncService, times(1)).handleCourseEvent(event);
        verify(channel, times(1)).basicAck(42L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("测试消息处理发生异常时触发 Channel 手动 basicNack(requeue=false)")
    void testOnMessage_Failure_NacksMessageToDlq() throws IOException {
        CourseDomainEvent event = CourseDomainEvent.builder()
                .messageId("msg_err_002")
                .eventType("CoursePublished")
                .aggregateId("1001")
                .aggregateVersion(1L)
                .build();

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        Message message = new Message("{}".getBytes(), properties);

        doThrow(new RuntimeException("ES cluster unavailable"))
                .when(indexSyncService).handleCourseEvent(event);

        courseIndexConsumer.onMessage(event, message, channel);

        verify(indexSyncService, times(1)).handleCourseEvent(event);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(99L, false, false);
    }

    @Test
    @DisplayName("测试 null 消息与空 Channel 防御性处理")
    void testOnMessage_NullMessageAndChannel_Safe() throws IOException {
        CourseDomainEvent event = CourseDomainEvent.builder().build();
        courseIndexConsumer.onMessage(event, null, null);
        verify(indexSyncService, times(1)).handleCourseEvent(event);
    }
}
