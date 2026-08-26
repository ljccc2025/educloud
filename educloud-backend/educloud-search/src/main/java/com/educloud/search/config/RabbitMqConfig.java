package com.educloud.search.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 领域事件驱动与实时索引同步配置类
 * 声明课程与内容 Topic 交换机、同步队列、死信交换机/队列与路由绑定。
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_COURSE_EVENTS = "educloud.course.events";
    public static final String EXCHANGE_CONTENT_EVENTS = "educloud.content.events";
    public static final String EXCHANGE_SEARCH_DLX = "search.sync.dlx";

    public static final String QUEUE_COURSE_SYNC = "search.course.sync.queue";
    public static final String QUEUE_CONTENT_SYNC = "search.content.sync.queue";
    public static final String QUEUE_SEARCH_DLQ = "search.sync.dlq";

    public static final String ROUTING_KEY_COURSE = "course.*";
    public static final String ROUTING_KEY_CONTENT = "content.*";
    public static final String ROUTING_KEY_DLQ = "search.sync.dlq";

    // 1. Topic 交换机声明
    @Bean
    public TopicExchange courseEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_COURSE_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange contentEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_CONTENT_EVENTS).durable(true).build();
    }

    // 2. 死信交换机声明
    @Bean
    public DirectExchange searchDeadLetterExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_SEARCH_DLX).durable(true).build();
    }

    // 3. 业务队列与死信队列声明
    @Bean
    public Queue courseSyncQueue() {
        return QueueBuilder.durable(QUEUE_COURSE_SYNC)
                .deadLetterExchange(EXCHANGE_SEARCH_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue contentSyncQueue() {
        return QueueBuilder.durable(QUEUE_CONTENT_SYNC)
                .deadLetterExchange(EXCHANGE_SEARCH_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue searchDeadLetterQueue() {
        return QueueBuilder.durable(QUEUE_SEARCH_DLQ).build();
    }

    // 4. 路由绑定关系
    @Bean
    public Binding courseSyncBinding(Queue courseSyncQueue, TopicExchange courseEventsExchange) {
        return BindingBuilder.bind(courseSyncQueue).to(courseEventsExchange).with(ROUTING_KEY_COURSE);
    }

    @Bean
    public Binding contentSyncBinding(Queue contentSyncQueue, TopicExchange contentEventsExchange) {
        return BindingBuilder.bind(contentSyncQueue).to(contentEventsExchange).with(ROUTING_KEY_CONTENT);
    }

    @Bean
    public Binding searchDlqBinding(Queue searchDeadLetterQueue, DirectExchange searchDeadLetterExchange) {
        return BindingBuilder.bind(searchDeadLetterQueue).to(searchDeadLetterExchange).with(ROUTING_KEY_DLQ);
    }

    // 5. JSON 消息转换器
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
