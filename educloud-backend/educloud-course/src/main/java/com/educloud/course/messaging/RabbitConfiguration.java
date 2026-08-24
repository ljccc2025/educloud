package com.educloud.course.messaging;

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
 * RabbitMQ 事件交换机配置。依据：M05 设计规格（交换机 educloud.events、vhost=educloud
 * （application.yml rabbitmq.virtual-host）、Jackson 信封序列化、发布器定时小批投递）。
 *
 * <p>M04 坑 3（B1 审查修复沿用）：交换机类型必须为 Topic（Direct 曾致 406
 * PRECONDITION_FAILED），使消费者可用 {@code Course.#} / {@code Enrollment.#} 通配
 * pattern 绑定，匹配 course 侧点分隔路由键 {@code Course.123} / {@code Enrollment.456}。
 * course 是纯生产者：业务队列由消费者（M06+）声明；此处 placeholder 队列绑定键保持字面
 * {@code educloud.events.placeholder}，保证交换机存在性校验。</p>
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

    /** 事件队列占位：业务消费者各自声明；此处保证交换机存在性校验。 */
    @Bean
    public Queue educloudEventPlaceholderQueue() {
        return new Queue("educloud.events.placeholder", true);
    }

    @Bean
    public Binding educloudEventPlaceholderBinding(
            @Qualifier("educloudEventPlaceholderQueue") Queue queue,
            @Qualifier("educloudEventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("educloud.events.placeholder");
    }

    public static final String ORDER_EVENT_EXCHANGE = "educloud.order.exchange";
    public static final String COURSE_ORDER_PAID_QUEUE = "educloud.course.order-paid.queue";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";

    @Bean
    public TopicExchange orderEventExchange() {
        return new TopicExchange(ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue courseOrderPaidQueue() {
        return new Queue(COURSE_ORDER_PAID_QUEUE, true);
    }

    @Bean
    public Binding courseOrderPaidBinding(
            @Qualifier("courseOrderPaidQueue") Queue queue,
            @Qualifier("orderEventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ORDER_PAID_ROUTING_KEY);
    }
}
