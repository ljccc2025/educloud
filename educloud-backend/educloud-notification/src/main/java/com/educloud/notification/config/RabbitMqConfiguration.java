package com.educloud.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    public static final String TOPIC_EXCHANGE_NAME = "educloud.events";
    public static final String NOTIFICATION_QUEUE_NAME = "educloud.notification.domain-events";

    public static final String ROUTING_KEY_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String ROUTING_KEY_ORDER_REFUNDED = "order.refunded";
    public static final String ROUTING_KEY_LIVE_STARTED = "live.started";
    public static final String ROUTING_KEY_ASSIGNMENT_GRADED = "assignment.graded";

    @Bean
    public TopicExchange educloudEventsExchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue notificationDomainQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE_NAME).build();
    }

    @Bean
    public Binding paymentSucceededBinding(Queue notificationDomainQueue, TopicExchange educloudEventsExchange) {
        return BindingBuilder.bind(notificationDomainQueue).to(educloudEventsExchange).with(ROUTING_KEY_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding orderRefundedBinding(Queue notificationDomainQueue, TopicExchange educloudEventsExchange) {
        return BindingBuilder.bind(notificationDomainQueue).to(educloudEventsExchange).with(ROUTING_KEY_ORDER_REFUNDED);
    }

    @Bean
    public Binding liveStartedBinding(Queue notificationDomainQueue, TopicExchange educloudEventsExchange) {
        return BindingBuilder.bind(notificationDomainQueue).to(educloudEventsExchange).with(ROUTING_KEY_LIVE_STARTED);
    }

    @Bean
    public Binding assignmentGradedBinding(Queue notificationDomainQueue, TopicExchange educloudEventsExchange) {
        return BindingBuilder.bind(notificationDomainQueue).to(educloudEventsExchange).with(ROUTING_KEY_ASSIGNMENT_GRADED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
