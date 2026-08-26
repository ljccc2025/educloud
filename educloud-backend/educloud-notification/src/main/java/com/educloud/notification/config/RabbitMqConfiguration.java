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

    // M10 修复：payment 服务的事件发往 educloud.payment.exchange（非 educloud.events 总线），
    // 通知中心需额外绑定该交换机的支付成功/退款路由，否则“购买成功/退款完成”通知链路断裂。
    public static final String PAYMENT_EXCHANGE_NAME = "educloud.payment.exchange";
    public static final String NOTIFICATION_PAYMENT_QUEUE_NAME = "educloud.notification.payment-events";

    public static final String ROUTING_KEY_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String ROUTING_KEY_PAYMENT_REFUNDED = "payment.refunded";
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

    /** 支付中心交换机（educloud.payment.exchange，payment 服务声明，此处幂等补声明）。 */
    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue notificationPaymentQueue() {
        return QueueBuilder.durable(NOTIFICATION_PAYMENT_QUEUE_NAME).build();
    }

    @Bean
    public Binding paymentSucceededNotifBinding(Queue notificationPaymentQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(notificationPaymentQueue).to(paymentEventsExchange).with(ROUTING_KEY_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding paymentRefundedNotifBinding(Queue notificationPaymentQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(notificationPaymentQueue).to(paymentEventsExchange).with(ROUTING_KEY_PAYMENT_REFUNDED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
