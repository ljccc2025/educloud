package com.educloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.PageResponse;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.order.dto.request.OrderCreateRequest;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.entity.CartItemEntity;
import com.educloud.order.entity.OrderStatus;
import com.educloud.order.entity.TradeOrderEntity;
import com.educloud.order.entity.TradeOrderItemEntity;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.feign.CourseClient;
import com.educloud.order.feign.dto.CourseSalesSnapshotDto;
import com.educloud.order.mapper.CartItemMapper;
import com.educloud.order.mapper.TradeOrderItemMapper;
import com.educloud.order.mapper.TradeOrderMapper;
import com.educloud.order.messaging.OrderDelayProducer;
import com.educloud.order.messaging.OutboxEventWriter;
import com.educloud.order.messaging.dto.OrderDelayMessage;
import com.educloud.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private TradeOrderMapper tradeOrderMapper;
    private TradeOrderItemMapper tradeOrderItemMapper;
    private CartItemMapper cartItemMapper;
    private CourseClient courseClient;
    private CartService cartService;
    private IdempotencyService idempotencyService;
    private IdentifierGenerator identifierGenerator;
    private OrderDelayProducer orderDelayProducer;
    private ObjectProvider<OrderDelayProducer> orderDelayProducerProvider;
    private OutboxEventWriter outboxEventWriter;
    private TransactionTemplate transactionTemplate;
    private OrderServiceImpl orderService;

    private final AtomicLong idSequence = new AtomicLong(10000L);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tradeOrderMapper = mock(TradeOrderMapper.class);
        tradeOrderItemMapper = mock(TradeOrderItemMapper.class);
        cartItemMapper = mock(CartItemMapper.class);
        courseClient = mock(CourseClient.class);
        cartService = mock(CartService.class);
        idempotencyService = mock(IdempotencyService.class);
        identifierGenerator = idSequence::incrementAndGet;
        orderDelayProducer = mock(OrderDelayProducer.class);
        orderDelayProducerProvider = mock(ObjectProvider.class);
        when(orderDelayProducerProvider.getIfAvailable()).thenReturn(orderDelayProducer);
        outboxEventWriter = mock(OutboxEventWriter.class);
        // BUG-019：DB 写入收敛到编程式短事务；mock 需真实执行 lambda，
        // 否则 insert/clearCart 的 verify 会因代码未执行而失败。
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        orderService = new OrderServiceImpl(
                tradeOrderMapper,
                tradeOrderItemMapper,
                cartItemMapper,
                courseClient,
                cartService,
                idempotencyService,
                identifierGenerator,
                orderDelayProducerProvider,
                outboxEventWriter,
                transactionTemplate);
    }

    @Test
    void createsOrderForSingleCourseDirectly() {
        Long studentId = 2001L;
        Long courseId = 9001L;
        String token = "tok-123";

        CourseSalesSnapshotDto course = CourseSalesSnapshotDto.builder()
                .id(courseId)
                .title("微服务架构深度实战")
                .coverFileId(8001L)
                .price(new BigDecimal("199.00"))
                .status("PUBLISHED")
                .isOnSale(true)
                .enrolled(false)
                .build();
        when(courseClient.getCourseDetail(courseId))
                .thenReturn(new ApiResponse<>("SUCCESS", "OK", course, "req-1", Instant.now()));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .courseId(courseId)
                .idempotencyToken(token)
                .build();

        OrderDetailResponse response = orderService.createOrder(studentId, request, null);

        verify(idempotencyService).validateAndConsume(studentId, token);
        verify(tradeOrderMapper).insert(any(TradeOrderEntity.class));
        verify(tradeOrderItemMapper).insert(any(TradeOrderItemEntity.class));
        verify(orderDelayProducer).sendDelayMessage(any(OrderDelayMessage.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        assertThat(response.getOriginalAmount()).isEqualByComparingTo("199.00");
        assertThat(response.getPayableAmount()).isEqualByComparingTo("199.00");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getCourseTitleSnapshot()).isEqualTo("微服务架构深度实战");
    }

    @Test
    void createsOrderFromCartAndClearsCart() {
        Long studentId = 2001L;
        String token = "tok-cart";

        CartItemEntity cart1 = CartItemEntity.builder().id(1L).studentId(studentId).courseId(9001L).selected(true).build();
        CartItemEntity cart2 = CartItemEntity.builder().id(2L).studentId(studentId).courseId(9002L).selected(true).build();
        when(cartItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cart1, cart2));

        CourseSalesSnapshotDto course1 = CourseSalesSnapshotDto.builder()
                .id(9001L).title("课程1").price(new BigDecimal("100.00")).status("PUBLISHED").isOnSale(true).build();
        CourseSalesSnapshotDto course2 = CourseSalesSnapshotDto.builder()
                .id(9002L).title("课程2").price(new BigDecimal("150.00")).status("PUBLISHED").isOnSale(true).build();

        when(courseClient.getCourseDetail(9001L)).thenReturn(new ApiResponse<>("SUCCESS", "OK", course1, "req-1", Instant.now()));
        when(courseClient.getCourseDetail(9002L)).thenReturn(new ApiResponse<>("SUCCESS", "OK", course2, "req-1", Instant.now()));

        OrderCreateRequest request = OrderCreateRequest.builder().idempotencyToken(token).build();
        OrderDetailResponse response = orderService.createOrder(studentId, request, null);

        verify(idempotencyService).validateAndConsume(studentId, token);
        verify(cartService).clearCart(studentId, true);
        verify(tradeOrderMapper).insert(any(TradeOrderEntity.class));
        verify(tradeOrderItemMapper, times(2)).insert(any(TradeOrderItemEntity.class));
        verify(orderDelayProducer).sendDelayMessage(any(OrderDelayMessage.class));

        assertThat(response.getPayableAmount()).isEqualByComparingTo("250.00");
        assertThat(response.getItems()).hasSize(2);
    }

    @Test
    void throwsWhenCourseNotOnSale() {
        Long studentId = 2001L;
        Long courseId = 9001L;

        CourseSalesSnapshotDto course = CourseSalesSnapshotDto.builder()
                .id(courseId)
                .title("下架课程")
                .price(new BigDecimal("199.00"))
                .status("OFFLINE")
                .isOnSale(true)
                .build();
        when(courseClient.getCourseDetail(courseId))
                .thenReturn(new ApiResponse<>("SUCCESS", "OK", course, "req-1", Instant.now()));

        OrderCreateRequest request = OrderCreateRequest.builder().courseId(courseId).build();

        assertThatThrownBy(() -> orderService.createOrder(studentId, request, "tok-1"))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.COURSE_NOT_ON_SALE);
    }

    @Test
    void throwsWhenCartIsEmpty() {
        Long studentId = 2001L;
        when(cartItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        OrderCreateRequest request = new OrderCreateRequest();

        assertThatThrownBy(() -> orderService.createOrder(studentId, request, "tok-1"))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.CART_EMPTY);
    }

    @Test
    void getsOrderDetail() {
        Long studentId = 2001L;
        Long orderId = 1001L;
        LocalDateTime now = LocalDateTime.now();

        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .orderNo("ORD1001")
                .studentId(studentId)
                .status(OrderStatus.PENDING_PAYMENT.name())
                .originalAmount(new BigDecimal("199.00"))
                .payableAmount(new BigDecimal("199.00"))
                .currency("CNY")
                .expiresAt(now.plusMinutes(15))
                .createdAt(now)
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
        when(tradeOrderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        OrderDetailResponse detail = orderService.getOrderDetail(studentId, orderId);

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(orderId);
        assertThat(detail.getOrderNo()).isEqualTo("ORD1001");
        assertThat(detail.getItems()).hasSize(1);
        assertThat(detail.getCountdownSeconds()).isGreaterThan(0L);
    }

    @Test
    void throwsWhenOrderDetailNotOwned() {
        Long studentId = 2001L;
        Long orderId = 1001L;

        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .studentId(9999L)
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        assertThatThrownBy(() -> orderService.getOrderDetail(studentId, orderId))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_OWNED);
    }

    @Test
    void cancelsPendingPaymentOrderSuccessfully() {
        Long studentId = 2001L;
        Long orderId = 1001L;

        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .studentId(studentId)
                .status(OrderStatus.PENDING_PAYMENT.name())
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);
        when(tradeOrderMapper.updateStatusToCancelledWithCas(eq(orderId), eq(OrderStatus.PENDING_PAYMENT.name()), eq(OrderStatus.CANCELLED.name()), any(LocalDateTime.class)))
                .thenReturn(1);

        orderService.cancelOrder(studentId, orderId);

        verify(tradeOrderMapper).updateStatusToCancelledWithCas(eq(orderId), eq(OrderStatus.PENDING_PAYMENT.name()), eq(OrderStatus.CANCELLED.name()), any(LocalDateTime.class));
    }

    @Test
    void throwsWhenCancellingPaidOrder() {
        Long studentId = 2001L;
        Long orderId = 1001L;

        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(orderId)
                .studentId(studentId)
                .status(OrderStatus.PAID.name())
                .build();
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelOrder(studentId, orderId))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_STATUS_INVALID);
    }
}
