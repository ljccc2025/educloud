package com.educloud.user.messaging;

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
 * RabbitMQ 事件交换机配置。依据：M03 设计规格第 9 节（交换机 educloud.events、
 * 路由键 aggregateType:aggregateId、Jackson 信封序列化、发布器定时小批投递）。
 *
 * <p>B1 审查修复：交换机类型由 Direct 改为 Topic（名称 educloud.events 与 bean 名
 * educloudEventExchange 不变），并新增 durable 队列 educloud.user.inbox.filedeleted，
 * 以 {@code FileObject.#} 通配 pattern 绑定（topic 下匹配 File 侧单段路由键
 * {@code FileObject:123}），打通 FileDeleted 事件 → User inbox 链路。placeholder
 * 队列绑定键保持字面 {@code educloud.events.placeholder}。
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

    /** FileDeleted Inbox 队列：File 侧 FileDeleted 事件（路由键 FileObject:fileId）经此入 inbox。 */
    @Bean
    public Queue educloudUserInboxFileDeletedQueue() {
        return new Queue("educloud.user.inbox.filedeleted", true);
    }

    @Bean
    public Binding educloudUserInboxFileDeletedBinding(
            @Qualifier("educloudUserInboxFileDeletedQueue") Queue queue,
            TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("FileObject.#");
    }
}
