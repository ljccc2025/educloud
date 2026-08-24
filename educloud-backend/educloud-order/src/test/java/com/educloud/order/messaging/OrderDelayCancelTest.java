package com.educloud.order.messaging;

import com.educloud.order.config.RabbitOrderConfig;
import com.educloud.order.entity.OrderStatus;
import com.educloud.order.entity.TradeOrderEntity;
import com.educloud.order.mapper.TradeOrderMapper;
import com.educloud.order.messaging.dto.OrderDelayMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderDelayCancelTest {

    private RabbitTemplate rabbitTemplate;
    private TradeOrderMapper tradeOrderMapper;
    private OrderDelayProducer producer;
    private OrderDelayConsumer consumer;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        tradeOrderMapper = mock(TradeOrderMapper.class);
        producer = new OrderDelayProducer(rabbitTemplate);
        consumer = new OrderDelayConsumer(tradeOrderMapper);
    }

    @Test
    void producerSendsDelayMessageToExchangeWithRoutingKey() {
        OrderDelayMessage message = OrderDelayMessage.builder()
                .orderId(1001L)
                .orderNo("ORD1001")
                .createdAt(LocalDateTime.now())
                .build();

        producer.sendDelayMessage(message);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitOrderConfig.ORDER_EXCHANGE),
                eq(RabbitOrderConfig.ORDER_DELAY_ROUTING_KEY),
                eq(message));
    }

    @Test
    void producerCatchesExceptionAndLogsWhenRabbitMqFails() {
        OrderDelayMessage message = OrderDelayMessage.builder()
                .orderId(1001L)
                .orderNo("ORD1001")
                .createdAt(LocalDateTime.now())
                .build();

        doThrow(new AmqpException("RabbitMQ connection down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> producer.sendDelayMessage(message))
                .doesNotThrowAnyException();
    }

    @Test
    void consumerCancelsPendingPaymentOrderViaCas() {
        Long orderId = 1001L;
        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .status(OrderStatus.PENDING_PAYMENT.name())
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);
        when(tradeOrderMapper.updateStatusToCancelledWithCas(
                eq(orderId), eq(OrderStatus.PENDING_PAYMENT.name()), eq(OrderStatus.CANCELLED.name()), any(LocalDateTime.class)))
                .thenReturn(1);

        OrderDelayMessage message = OrderDelayMessage.builder()
                .orderId(orderId)
                .orderNo("ORD1001")
                .build();

        consumer.onOrderTimeout(message);

        verify(tradeOrderMapper).updateStatusToCancelledWithCas(
                eq(orderId), eq(OrderStatus.PENDING_PAYMENT.name()), eq(OrderStatus.CANCELLED.name()), any(LocalDateTime.class));
    }

    @Test
    void consumerSkipsAlreadyPaidOrder() {
        Long orderId = 1001L;
        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .status(OrderStatus.PAID.name())
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        OrderDelayMessage message = OrderDelayMessage.builder()
                .orderId(orderId)
                .orderNo("ORD1001")
                .build();

        consumer.onOrderTimeout(message);

        verify(tradeOrderMapper, never()).updateStatusToCancelledWithCas(any(), any(), any(), any());
    }

    @Test
    void consumerSkipsAlreadyCancelledOrder() {
        Long orderId = 1001L;
        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .status(OrderStatus.CANCELLED.name())
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        OrderDelayMessage message = OrderDelayMessage.builder()
                .orderId(orderId)
                .orderNo("ORD1001")
                .build();

        consumer.onOrderTimeout(message);

        verify(tradeOrderMapper, never()).updateStatusToCancelledWithCas(any(), any(), any(), any());
    }
}
