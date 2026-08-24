package com.educloud.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class RabbitOrderConfig {

    public static final String ORDER_EXCHANGE = "educloud.order.direct.exchange";
    public static final String ORDER_DELAY_QUEUE = "educloud.order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    public static final String ORDER_DLX_EXCHANGE = "educloud.order.dlx.exchange";
    public static final String ORDER_CANCEL_QUEUE = "educloud.order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    public static final int ORDER_TTL_MS = 900000; // 15 minutes

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange orderDirectExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(ORDER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", ORDER_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY);
        args.put("x-message-ttl", ORDER_TTL_MS);
        return new Queue(ORDER_DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue orderCancelQueue() {
        return new Queue(ORDER_CANCEL_QUEUE, true, false, false);
    }

    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, DirectExchange orderDirectExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderDirectExchange).with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelBinding(Queue orderCancelQueue, DirectExchange orderDlxExchange) {
        return BindingBuilder.bind(orderCancelQueue).to(orderDlxExchange).with(ORDER_CANCEL_ROUTING_KEY);
    }
}
