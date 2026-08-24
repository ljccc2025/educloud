package com.educloud.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.order.mapper.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMapperTest {

    @Test
    void testOrderStatusEnum() {
        assertThat(OrderStatus.valueOf("PENDING_PAYMENT")).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(OrderStatus.valueOf("PAID")).isEqualTo(OrderStatus.PAID);
        assertThat(OrderStatus.valueOf("CANCELLED")).isEqualTo(OrderStatus.CANCELLED);
        assertThat(OrderStatus.valueOf("REFUNDED")).isEqualTo(OrderStatus.REFUNDED);
        assertThat(OrderStatus.values()).hasSize(4);
    }

    @Test
    void testFulfillmentStatusEnum() {
        assertThat(FulfillmentStatus.valueOf("UNFULFILLED")).isEqualTo(FulfillmentStatus.UNFULFILLED);
        assertThat(FulfillmentStatus.valueOf("FULFILLED")).isEqualTo(FulfillmentStatus.FULFILLED);
        assertThat(FulfillmentStatus.valueOf("REVOKED")).isEqualTo(FulfillmentStatus.REVOKED);
        assertThat(FulfillmentStatus.values()).hasSize(3);
    }

    @Test
    void testRefundStatusEnum() {
        assertThat(RefundStatus.valueOf("PENDING_REVIEW")).isEqualTo(RefundStatus.PENDING_REVIEW);
        assertThat(RefundStatus.valueOf("APPROVED")).isEqualTo(RefundStatus.APPROVED);
        assertThat(RefundStatus.valueOf("REJECTED")).isEqualTo(RefundStatus.REJECTED);
        assertThat(RefundStatus.valueOf("SUCCESS")).isEqualTo(RefundStatus.SUCCESS);
        assertThat(RefundStatus.values()).hasSize(4);
    }

    @Test
    void testCartItemEntity() {
        LocalDateTime now = LocalDateTime.now();
        CartItemEntity cartItem = CartItemEntity.builder()
                .id(100L)
                .studentId(200L)
                .courseId(300L)
                .selected(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(cartItem.getId()).isEqualTo(100L);
        assertThat(cartItem.getStudentId()).isEqualTo(200L);
        assertThat(cartItem.getCourseId()).isEqualTo(300L);
        assertThat(cartItem.getSelected()).isTrue();
        assertThat(cartItem.getCreatedAt()).isEqualTo(now);
        assertThat(cartItem.getUpdatedAt()).isEqualTo(now);

        TableName tableName = CartItemEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("cart_item");
    }

    @Test
    void testTradeOrderEntity() throws NoSuchFieldException {
        LocalDateTime now = LocalDateTime.now();
        TradeOrderEntity order = TradeOrderEntity.builder()
                .id(100L)
                .orderNo("ORD123")
                .studentId(200L)
                .status(OrderStatus.PENDING_PAYMENT.name())
                .originalAmount(new BigDecimal("199.00"))
                .payableAmount(new BigDecimal("199.00"))
                .currency("CNY")
                .expiresAt(now.plusMinutes(15))
                .paidAt(null)
                .cancelledAt(null)
                .idempotencyKeyHash("hash123")
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(order.getId()).isEqualTo(100L);
        assertThat(order.getOrderNo()).isEqualTo("ORD123");
        assertThat(order.getStudentId()).isEqualTo(200L);
        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.getOriginalAmount()).isEqualByComparingTo("199.00");
        assertThat(order.getPayableAmount()).isEqualByComparingTo("199.00");
        assertThat(order.getVersion()).isEqualTo(0);

        TableName tableName = TradeOrderEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("trade_order");

        Field versionField = TradeOrderEntity.class.getDeclaredField("version");
        assertThat(versionField.isAnnotationPresent(Version.class)).isTrue();
    }

    @Test
    void testTradeOrderItemEntity() {
        LocalDateTime now = LocalDateTime.now();
        TradeOrderItemEntity item = TradeOrderItemEntity.builder()
                .id(101L)
                .orderId(100L)
                .courseId(300L)
                .courseTitleSnapshot("Spring Cloud 实战")
                .coverFileIdSnapshot(400L)
                .unitPrice(new BigDecimal("199.00"))
                .quantity(1)
                .lineAmount(new BigDecimal("199.00"))
                .refundReservedAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .fulfillmentStatus(FulfillmentStatus.UNFULFILLED.name())
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(item.getId()).isEqualTo(101L);
        assertThat(item.getOrderId()).isEqualTo(100L);
        assertThat(item.getCourseId()).isEqualTo(300L);
        assertThat(item.getCourseTitleSnapshot()).isEqualTo("Spring Cloud 实战");
        assertThat(item.getCoverFileIdSnapshot()).isEqualTo(400L);
        assertThat(item.getLineAmount()).isEqualByComparingTo("199.00");
        assertThat(item.getFulfillmentStatus()).isEqualTo("UNFULFILLED");

        TableName tableName = TradeOrderItemEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("trade_order_item");
    }

    @Test
    void testRefundRequestEntity() throws NoSuchFieldException {
        LocalDateTime now = LocalDateTime.now();
        RefundRequestEntity refund = RefundRequestEntity.builder()
                .id(500L)
                .refundNo("RFD123")
                .orderId(100L)
                .studentId(200L)
                .requestedAmount(new BigDecimal("199.00"))
                .reason("课程不适合")
                .status(RefundStatus.PENDING_REVIEW.name())
                .reviewedBy(null)
                .reviewReason(null)
                .reviewedAt(null)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(refund.getId()).isEqualTo(500L);
        assertThat(refund.getRefundNo()).isEqualTo("RFD123");
        assertThat(refund.getRequestedAmount()).isEqualByComparingTo("199.00");
        assertThat(refund.getStatus()).isEqualTo("PENDING_REVIEW");

        TableName tableName = RefundRequestEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("refund_request");

        Field versionField = RefundRequestEntity.class.getDeclaredField("version");
        assertThat(versionField.isAnnotationPresent(Version.class)).isTrue();
    }

    @Test
    void testRefundRequestItemEntity() {
        LocalDateTime now = LocalDateTime.now();
        RefundRequestItemEntity refundItem = RefundRequestItemEntity.builder()
                .id(501L)
                .refundRequestId(500L)
                .orderItemId(101L)
                .courseId(300L)
                .requestedAmount(new BigDecimal("199.00"))
                .approvedAmount(BigDecimal.ZERO)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(refundItem.getId()).isEqualTo(501L);
        assertThat(refundItem.getRefundRequestId()).isEqualTo(500L);
        assertThat(refundItem.getOrderItemId()).isEqualTo(101L);
        assertThat(refundItem.getRequestedAmount()).isEqualByComparingTo("199.00");

        TableName tableName = RefundRequestItemEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("refund_request_item");
    }

    @Test
    void testOutboxEventAndSequenceEntity() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(600L)
                .eventId("evt-123")
                .aggregateType("Order")
                .aggregateId("100")
                .eventType("OrderPaid")
                .eventVersion(1)
                .aggregateVersion(1L)
                .payloadJson("{}")
                .requestId("req-123")
                .publishStatus("PENDING")
                .build();
        assertThat(event.getEventId()).isEqualTo("evt-123");

        OutboxSequenceEntity seq = OutboxSequenceEntity.builder()
                .sourceName("educloud-order")
                .lastValue(10L)
                .build();
        assertThat(seq.getSourceName()).isEqualTo("educloud-order");
        assertThat(seq.getLastValue()).isEqualTo(10L);
    }

    @Test
    void testMappersAreBaseMappers() {
        assertThat(BaseMapper.class).isAssignableFrom(CartItemMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(TradeOrderMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(TradeOrderItemMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(RefundRequestMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(RefundRequestItemMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(OutboxEventMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(OutboxSequenceMapper.class);
    }
}
