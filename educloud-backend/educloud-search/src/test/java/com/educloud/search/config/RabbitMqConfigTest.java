package com.educloud.search.config;

import com.educloud.search.document.LessonDoc;
import com.educloud.search.messaging.event.ContentDomainEvent;
import com.educloud.search.messaging.event.CourseDomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqConfigTest {

    private final RabbitMqConfig rabbitMqConfig = new RabbitMqConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("测试 RabbitMQ 交换机、队列与绑定声明")
    void testRabbitMqDeclarations() {
        TopicExchange courseExchange = rabbitMqConfig.courseEventsExchange();
        assertThat(courseExchange.getName()).isEqualTo(RabbitMqConfig.EXCHANGE_COURSE_EVENTS);
        assertThat(courseExchange.isDurable()).isTrue();

        TopicExchange contentExchange = rabbitMqConfig.contentEventsExchange();
        assertThat(contentExchange.getName()).isEqualTo(RabbitMqConfig.EXCHANGE_CONTENT_EVENTS);
        assertThat(contentExchange.isDurable()).isTrue();

        DirectExchange dlxExchange = rabbitMqConfig.searchDeadLetterExchange();
        assertThat(dlxExchange.getName()).isEqualTo(RabbitMqConfig.EXCHANGE_SEARCH_DLX);
        assertThat(dlxExchange.isDurable()).isTrue();

        Queue courseQueue = rabbitMqConfig.courseSyncQueue();
        assertThat(courseQueue.getName()).isEqualTo(RabbitMqConfig.QUEUE_COURSE_SYNC);
        assertThat(courseQueue.getArguments().get("x-dead-letter-exchange")).isEqualTo(RabbitMqConfig.EXCHANGE_SEARCH_DLX);
        assertThat(courseQueue.getArguments().get("x-dead-letter-routing-key")).isEqualTo(RabbitMqConfig.ROUTING_KEY_DLQ);

        Queue contentQueue = rabbitMqConfig.contentSyncQueue();
        assertThat(contentQueue.getName()).isEqualTo(RabbitMqConfig.QUEUE_CONTENT_SYNC);
        assertThat(contentQueue.getArguments().get("x-dead-letter-exchange")).isEqualTo(RabbitMqConfig.EXCHANGE_SEARCH_DLX);

        Queue dlqQueue = rabbitMqConfig.searchDeadLetterQueue();
        assertThat(dlqQueue.getName()).isEqualTo(RabbitMqConfig.QUEUE_SEARCH_DLQ);

        Binding courseBinding = rabbitMqConfig.courseSyncBinding(courseQueue, courseExchange);
        assertThat(courseBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.ROUTING_KEY_COURSE);

        Binding contentBinding = rabbitMqConfig.contentSyncBinding(contentQueue, contentExchange);
        assertThat(contentBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.ROUTING_KEY_CONTENT);

        Binding dlqBinding = rabbitMqConfig.searchDlqBinding(dlqQueue, dlxExchange);
        assertThat(dlqBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.ROUTING_KEY_DLQ);

        MessageConverter converter = rabbitMqConfig.jsonMessageConverter();
        assertThat(converter).isNotNull();
    }

    @Test
    @DisplayName("测试 CourseDomainEvent 与 ContentDomainEvent JSON 序列化与反序列化")
    void testEventModelsSerialization() throws Exception {
        CourseDomainEvent courseEvent = CourseDomainEvent.builder()
                .messageId("msg_test_001")
                .eventType("CoursePublished")
                .aggregateType("Course")
                .aggregateId("2001")
                .aggregateVersion(1L)
                .sourceService("educloud-course")
                .data(CourseDomainEvent.CourseEventData.builder()
                        .courseId(2001L)
                        .title("Java 微服务")
                        .priceCents(9900L)
                        .lessons(List.of(LessonDoc.builder().id("1").title("Lesson 1").build()))
                        .build())
                .build();

        String courseJson = objectMapper.writeValueAsString(courseEvent);
        assertThat(courseJson).contains("msg_test_001");
        assertThat(courseJson).contains("Java 微服务");

        CourseDomainEvent deserializedCourse = objectMapper.readValue(courseJson, CourseDomainEvent.class);
        assertThat(deserializedCourse.getEffectiveMessageId()).isEqualTo("msg_test_001");
        assertThat(deserializedCourse.getData().getTitle()).isEqualTo("Java 微服务");

        ContentDomainEvent contentEvent = ContentDomainEvent.builder()
                .messageId("msg_cnt_001")
                .eventType("LessonPublished")
                .aggregateType("CourseContent")
                .aggregateId("3001")
                .aggregateVersion(2L)
                .data(ContentDomainEvent.ContentEventData.builder()
                        .courseId(2001L)
                        .lessonId(101L)
                        .title("Lesson 101")
                        .build())
                .build();

        String contentJson = objectMapper.writeValueAsString(contentEvent);
        ContentDomainEvent deserializedContent = objectMapper.readValue(contentJson, ContentDomainEvent.class);
        assertThat(deserializedContent.getEffectiveMessageId()).isEqualTo("msg_cnt_001");
        assertThat(deserializedContent.getData().getTitle()).isEqualTo("Lesson 101");
    }
}
