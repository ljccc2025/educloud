package com.educloud.file.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ 事件交换机配置。依据：M04 设计规格第 8/9 节（交换机 educloud.events、
 * 路由键 aggregateType:aggregateId、Jackson 信封序列化、发布器定时小批投递）。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class RabbitConfiguration {

    public static final String EVENT_EXCHANGE = "educloud.events";

    @Bean
    public DirectExchange educloudEventExchange() {
        return new DirectExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setExchange(EVENT_EXCHANGE);
        return template;
    }

    /** 事件队列占位：业务消费者（M04+）各自声明；此处保证交换机存在性校验。 */
    @Bean
    public Queue educloudEventPlaceholderQueue() {
        return new Queue("educloud.events.placeholder", true);
    }

    @Bean
    public Binding educloudEventPlaceholderBinding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("educloud.events.placeholder");
    }
}
