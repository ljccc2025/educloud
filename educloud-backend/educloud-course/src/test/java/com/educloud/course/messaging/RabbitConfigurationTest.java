package com.educloud.course.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RabbitConfiguration 交换机/绑定测试。M04 坑 3：事件交换机必须是 Topic（Direct 曾致 406
 * PRECONDITION_FAILED），名称保持 educloud.events；placeholder 队列绑定字面键
 * educloud.events.placeholder。course 是纯生产者，业务队列（Course.#/Enrollment.#）由
 * 消费者声明，此处只保证交换机与发布模板存在。
 */
class RabbitConfigurationTest {

    private final RabbitConfiguration configuration = new RabbitConfiguration();

    @Test
    void exchangeIsTopicExchangeWithStableName() {
        TopicExchange exchange = configuration.educloudEventExchange();
        assertThat(exchange.getName()).isEqualTo(RabbitConfiguration.EVENT_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
    }

    @Test
    void placeholderQueueBindingKeepsLiteralRoutingKey() {
        Queue queue = configuration.educloudEventPlaceholderQueue();
        Binding binding = configuration.educloudEventPlaceholderBinding(
                queue, configuration.educloudEventExchange());
        assertThat(binding.getRoutingKey()).isEqualTo("educloud.events.placeholder");
        assertThat(binding.getExchange()).isEqualTo(RabbitConfiguration.EVENT_EXCHANGE);
    }
}
