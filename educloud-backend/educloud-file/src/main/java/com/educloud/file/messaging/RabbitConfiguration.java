package com.educloud.file.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ 事件交换机配置。依据：M04 设计规格第 8/9 节（交换机 educloud.events、
 * 路由键 aggregateType:aggregateId、Jackson 信封序列化、发布器定时小批投递）。
 *
 * <p>B1 审查修复：交换机类型由 Direct 改为 Topic（名称 educloud.events 与 bean 名
 * educloudEventExchange 不变），使消费者可用 {@code FileObject.#} 等通配 pattern 绑定
 * （Direct 交换机不支持通配）。placeholder 队列绑定键保持字面
 * {@code educloud.events.placeholder}，topic 下精确键仍可匹配。
 *
 * <p>运维注意：RabbitMQ 中已存在的同名 Direct 交换机不能直接改类型，需删除重建
 * （educloud.events），重建期间短暂无事件路由；本机测试使用 mock，不连真实 RabbitMQ。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class RabbitConfiguration {

    public static final String EVENT_EXCHANGE = "educloud.events";

    @Bean
    public TopicExchange educloudEventExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
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
    public Binding educloudEventPlaceholderBinding(
            @Qualifier("educloudEventPlaceholderQueue") Queue queue,
            TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("educloud.events.placeholder");
    }
}
