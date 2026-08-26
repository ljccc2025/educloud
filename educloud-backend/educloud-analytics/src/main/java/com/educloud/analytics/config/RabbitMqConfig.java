package com.educloud.analytics.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_USER_EVENTS = "educloud.user.events";
    public static final String EXCHANGE_COURSE_EVENTS = "educloud.course.events";
    public static final String EXCHANGE_PAYMENT_EVENTS = "educloud.payment.events";
    public static final String EXCHANGE_CONTENT_EVENTS = "educloud.content.events";
    public static final String EXCHANGE_AUDIT_EVENTS = "educloud.audit.events";
    public static final String EXCHANGE_ANALYTICS_DLX = "analytics.sync.dlx";

    public static final String QUEUE_ANALYTICS_USER = "analytics.user.events.queue";
    public static final String QUEUE_ANALYTICS_COURSE = "analytics.course.events.queue";
    public static final String QUEUE_ANALYTICS_PAYMENT = "analytics.payment.events.queue";
    public static final String QUEUE_ANALYTICS_CONTENT = "analytics.content.events.queue";
    public static final String QUEUE_ANALYTICS_AUDIT = "analytics.audit.events.queue";
    public static final String QUEUE_ANALYTICS_DLQ = "analytics.sync.dlq";

    public static final String ROUTING_KEY_DLQ = "analytics.sync.dlq";

    @Bean
    public TopicExchange userEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_USER_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange courseEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_COURSE_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_PAYMENT_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange contentEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_CONTENT_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange auditEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_AUDIT_EVENTS).durable(true).build();
    }

    @Bean
    public DirectExchange analyticsDeadLetterExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_ANALYTICS_DLX).durable(true).build();
    }

    @Bean
    public Queue analyticsUserQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_USER)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsCourseQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_COURSE)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsPaymentQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_PAYMENT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsContentQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_CONTENT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsAuditQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_AUDIT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsDeadLetterQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_DLQ).build();
    }

    @Bean
    public Binding userBinding(Queue analyticsUserQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(analyticsUserQueue).to(userEventsExchange).with("user.*");
    }

    @Bean
    public Binding courseBinding(Queue analyticsCourseQueue, TopicExchange courseEventsExchange) {
        return BindingBuilder.bind(analyticsCourseQueue).to(courseEventsExchange).with("course.*");
    }

    @Bean
    public Binding paymentBinding(Queue analyticsPaymentQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(analyticsPaymentQueue).to(paymentEventsExchange).with("payment.*");
    }

    @Bean
    public Binding contentBinding(Queue analyticsContentQueue, TopicExchange contentEventsExchange) {
        return BindingBuilder.bind(analyticsContentQueue).to(contentEventsExchange).with("content.*");
    }

    @Bean
    public Binding auditBinding(Queue analyticsAuditQueue, TopicExchange auditEventsExchange) {
        return BindingBuilder.bind(analyticsAuditQueue).to(auditEventsExchange).with("audit.*");
    }

    @Bean
    public Binding analyticsDlqBinding(Queue analyticsDeadLetterQueue, DirectExchange analyticsDeadLetterExchange) {
        return BindingBuilder.bind(analyticsDeadLetterQueue).to(analyticsDeadLetterExchange).with(ROUTING_KEY_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
