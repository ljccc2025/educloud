package com.educloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.entity.OrderStatus;
import com.educloud.order.entity.TradeOrderEntity;
import com.educloud.order.entity.TradeOrderItemEntity;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.feign.CourseClient;
import com.educloud.order.mapper.CartItemMapper;
import com.educloud.order.mapper.TradeOrderItemMapper;
import com.educloud.order.mapper.TradeOrderMapper;
import com.educloud.order.messaging.OrderDelayProducer;
import com.educloud.order.messaging.OrderEventPublisher;
import com.educloud.order.messaging.dto.OrderPaidEvent;
import com.educloud.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderMockPayTest {

    private TradeOrderMapper tradeOrderMapper;
    private TradeOrderItemMapper tradeOrderItemMapper;
    private CartItemMapper cartItemMapper;
    private CourseClient courseClient;
    private CartService cartService;
    private IdempotencyService idempotencyService;
    private IdentifierGenerator identifierGenerator;
    private OrderDelayProducer orderDelayProducer;
    private OrderEventPublisher orderEventPublisher;
    private ObjectProvider<OrderDelayProducer> orderDelayProducerProvider;
    private ObjectProvider<OrderEventPublisher> orderEventPublisherProvider;
    private OrderServiceImpl orderService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tradeOrderMapper = mock(TradeOrderMapper.class);
        tradeOrderItemMapper = mock(TradeOrderItemMapper.class);
        cartItemMapper = mock(CartItemMapper.class);
        courseClient = mock(CourseClient.class);
        cartService = mock(CartService.class);
        idempotencyService = mock(IdempotencyService.class);
        identifierGenerator = () -> 10001L;
        orderDelayProducer = mock(OrderDelayProducer.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        orderDelayProducerProvider = mock(ObjectProvider.class);
        orderEventPublisherProvider = mock(ObjectProvider.class);
        when(orderDelayProducerProvider.getIfAvailable()).thenReturn(orderDelayProducer);
        when(orderEventPublisherProvider.getIfAvailable()).thenReturn(orderEventPublisher);

        orderService = new OrderServiceImpl(
                tradeOrderMapper,
                tradeOrderItemMapper,
                cartItemMapper,
                courseClient,
                cartService,
                idempotencyService,
                identifierGenerator,
                orderDelayProducerProvider,
                orderEventPublisherProvider);
    }

    @Test
    void mockPayTransitionsStatusToPaidAndPublishesOrderPaidEvent() {
        Long studentId = 2001L;
        Long orderId = 1001L;
        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .orderNo("ORD1001")
                .studentId(studentId)
                .status(OrderStatus.PENDING_PAYMENT.name())
                .payableAmount(new BigDecimal("199.00"))
                .currency("CNY")
                .build();
        TradeOrderItemEntity item = TradeOrderItemEntity.builder()
                .id(5001L)
                .orderId(orderId)
                .courseId(9001L)
                .courseTitleSnapshot("微服务实战")
                .unitPrice(new BigDecimal("199.00"))
                .quantity(1)
                .lineAmount(new BigDecimal("199.00"))
                .fulfillmentStatus("UNFULFILLED")
                .build();

        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);
        when(tradeOrderMapper.updateStatusToPaidWithCas(eq(orderId), eq(OrderStatus.PENDING_PAYMENT.name()), eq(OrderStatus.PAID.name()), any(LocalDateTime.class)))
                .thenReturn(1);
        when(tradeOrderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        OrderDetailResponse response = orderService.mockPay(studentId, orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAID.name());

        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(orderEventPublisher).publishOrderPaid(captor.capture());
        OrderPaidEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(orderId);
        assertThat(event.getStudentId()).isEqualTo(studentId);
        assertThat(event.getCourseIds()).containsExactly(9001L);
        assertThat(event.getPaidAmount()).isEqualByComparingTo("199.00");
    }

    @Test
    void mockPayThrowsWhenOrderNotOwned() {
        Long studentId = 2001L;
        Long orderId = 1001L;
        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .studentId(9999L)
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        assertThatThrownBy(() -> orderService.mockPay(studentId, orderId))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_OWNED);
    }

    @Test
    void mockPayThrowsWhenOrderNotPendingPayment() {
        Long studentId = 2001L;
        Long orderId = 1001L;
        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .studentId(studentId)
                .status(OrderStatus.CANCELLED.name())
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        assertThatThrownBy(() -> orderService.mockPay(studentId, orderId))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_STATUS_INVALID);
    }
}
