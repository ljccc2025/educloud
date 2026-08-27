package com.educloud.content.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ 事件交换机配置（角色化动态流阶段 2）：content 模块补建 Outbox 投递链路。
 *
 * <p>事件路由（与 educloud-analytics 动态流队列绑定对齐）：</p>
 * <ul>
 *   <li>内容域交换机 {@code educloud.content.events}：作业提交（{@code assignment.submitted}）、
 *       完课（{@code course.completed}）、证书（{@code certificate.issued}）、
 *       内容修订发布（{@code content.revision.published}）；analytics 动态流内容队列以
 *       {@code #} 通配绑定。</li>
 *   <li>全域总线 {@code educloud.events}：作业批改（{@code assignment.graded}），
 *       analytics 动态流作业队列与 notification 均按该路由键定向订阅。</li>
 * </ul>
 *
 * <p>content 是纯生产者：业务队列由消费者声明；此处仅声明交换机与占位队列，
 * 保证交换机存在性校验。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class RabbitConfiguration {

    public static final String CONTENT_EVENT_EXCHANGE = "educloud.content.events";
    public static final String DOMAIN_EVENT_EXCHANGE = "educloud.events";

    @Bean
    public TopicExchange contentEventExchange() {
        return new TopicExchange(CONTENT_EVENT_EXCHANGE, true, false);
    }

    /** 全域事件总线：作业批改事件（routing key assignment.graded）发布在该交换机。 */
    @Bean
    public TopicExchange domainEventExchange() {
        return new TopicExchange(DOMAIN_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 投递器使用显式交换机参数发布（不设默认交换机），支持双交换机路由。 */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    /** 事件队列占位：业务消费者各自声明；此处保证交换机存在性校验。 */
    @Bean
    public Queue contentEventPlaceholderQueue() {
        return new Queue("educloud.content.events.placeholder", true);
    }

    @Bean
    public Binding contentEventPlaceholderBinding(
            @Qualifier("contentEventPlaceholderQueue") Queue queue,
            @Qualifier("contentEventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("educloud.content.events.placeholder");
    }
}
