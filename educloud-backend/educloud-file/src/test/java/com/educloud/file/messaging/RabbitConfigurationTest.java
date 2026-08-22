package com.educloud.file.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RabbitConfiguration 交换机/绑定测试。B1 审查修复：事件交换机由 Direct 改为 Topic
 * （FileObject.# 通配绑定）；交换机名 educloud.events 与 placeholder 字面绑定键保持稳定。
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
