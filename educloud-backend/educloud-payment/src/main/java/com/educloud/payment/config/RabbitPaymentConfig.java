package com.educloud.payment.config;

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
public class RabbitPaymentConfig {

    public static final String PAYMENT_EXCHANGE = "educloud.payment.exchange";
    public static final String ROUTING_KEY_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String ROUTING_KEY_PAYMENT_REFUNDED = "payment.refunded";

    public static final String QUEUE_ORDER_PAYMENT_SUCCESS = "order.payment.success.queue";
    public static final String QUEUE_ORDER_PAYMENT_REFUND = "order.payment.refund.queue";
    public static final String QUEUE_COURSE_PAYMENT_REFUND = "course.payment.refund.queue";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaymentSuccessQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_PAYMENT_SUCCESS).build();
    }

    @Bean
    public Binding orderPaymentSuccessBinding(Queue orderPaymentSuccessQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(orderPaymentSuccessQueue).to(paymentExchange).with(ROUTING_KEY_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Queue orderPaymentRefundQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_PAYMENT_REFUND).build();
    }

    @Bean
    public Binding orderPaymentRefundBinding(Queue orderPaymentRefundQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(orderPaymentRefundQueue).to(paymentExchange).with(ROUTING_KEY_PAYMENT_REFUNDED);
    }

    @Bean
    public Queue coursePaymentRefundQueue() {
        return QueueBuilder.durable(QUEUE_COURSE_PAYMENT_REFUND).build();
    }

    @Bean
    public Binding coursePaymentRefundBinding(Queue coursePaymentRefundQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(coursePaymentRefundQueue).to(paymentExchange).with(ROUTING_KEY_PAYMENT_REFUNDED);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
