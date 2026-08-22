package com.educloud.user.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RabbitConfiguration 交换机/队列/绑定测试。B1 审查修复：事件交换机改为 Topic，
 * 新增 durable 队列 educloud.user.inbox.filedeleted 并以 FileObject.# 通配绑定
 * （匹配 File 侧 aggregateType:aggregateId 单段路由键，如 FileObject:123）。
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
    void fileDeletedInboxQueueIsDurableAndBoundWithTopicPattern() {
        Queue queue = configuration.educloudUserInboxFileDeletedQueue();
        assertThat(queue.getName()).isEqualTo("educloud.user.inbox.filedeleted");
        assertThat(queue.isDurable()).isTrue();

        Binding binding = configuration.educloudUserInboxFileDeletedBinding(
                queue, configuration.educloudEventExchange());
        assertThat(binding.getRoutingKey()).isEqualTo("FileObject.#");
        assertThat(binding.getExchange()).isEqualTo(RabbitConfiguration.EVENT_EXCHANGE);
        assertThat(binding.getDestination()).isEqualTo("educloud.user.inbox.filedeleted");
    }

    @Test
    void placeholderBindingKeepsLiteralRoutingKey() {
        Queue queue = configuration.educloudEventPlaceholderQueue();
        Binding binding = configuration.educloudEventPlaceholderBinding(
                queue, configuration.educloudEventExchange());
        assertThat(binding.getRoutingKey()).isEqualTo("educloud.events.placeholder");
    }
}
