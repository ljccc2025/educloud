package com.educloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.PageResponse;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.order.dto.request.OrderCreateRequest;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.dto.response.OrderFulfillmentSnapshotResponse;
import com.educloud.order.dto.response.OrderItemResponse;
import com.educloud.order.dto.response.OrderPayableSnapshotResponse;
import com.educloud.order.entity.*;
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
import com.educloud.order.messaging.dto.OrderPaidEvent;
import com.educloud.order.service.CartService;
import com.educloud.order.service.IdempotencyService;
import com.educloud.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int ORDER_EXPIRE_MINUTES = 15;

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final CourseClient courseClient;
    private final CartService cartService;
    private final IdempotencyService idempotencyService;
    private final IdentifierGenerator identifierGenerator;
    private final ObjectProvider<OrderDelayProducer> orderDelayProducerProvider;
    private final OutboxEventWriter outboxEventWriter;
    private final TransactionTemplate transactionTemplate;

    @Override
    public OrderDetailResponse createOrder(Long studentId, OrderCreateRequest request, String headerIdempotencyToken) {
        String token = (headerIdempotencyToken != null && !headerIdempotencyToken.isBlank())
                ? headerIdempotencyToken
                : (request != null ? request.getIdempotencyToken() : null);

        idempotencyService.validateAndConsume(studentId, token);

        List<CourseSalesSnapshotDto> coursesToBuy = new ArrayList<>();
        boolean isCartBuy = (request == null || request.getCourseId() == null);

        // BUG-019 修复：Redis 幂等消费 + Feign 课程快照拉取/校验均在事务外，
        // 慢 RPC 不再长期占用数据库连接；DB 写入收敛到下方编程式短事务。
        if (isCartBuy) {
            LambdaQueryWrapper<CartItemEntity> cartQuery = new LambdaQueryWrapper<CartItemEntity>()
                    .eq(CartItemEntity::getStudentId, studentId)
                    .eq(CartItemEntity::getSelected, true);
            List<CartItemEntity> selectedItems = cartItemMapper.selectList(cartQuery);
            if (selectedItems == null || selectedItems.isEmpty()) {
                throw new OrderBizException(OrderErrorCode.CART_EMPTY);
            }
            for (CartItemEntity item : selectedItems) {
                CourseSalesSnapshotDto snapshot = fetchAndValidateCourse(item.getCourseId());
                coursesToBuy.add(snapshot);
            }
        } else {
            CourseSalesSnapshotDto snapshot = fetchAndValidateCourse(request.getCourseId());
            coursesToBuy.add(snapshot);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(ORDER_EXPIRE_MINUTES);
        Long orderId = identifierGenerator.nextId();
        String orderNo = "ORD" + orderId;

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<TradeOrderItemEntity> orderItemEntities = new ArrayList<>(coursesToBuy.size());

        for (CourseSalesSnapshotDto course : coursesToBuy) {
            // BUG-021 修复：金额合法性校验（非负、scale<=2），脏价格拒单不入库，
            // 防止负金额/高精度截断订单驱动下游履约（资损级防护）。
            BigDecimal price = course.getPrice();
            if (price == null || price.signum() < 0 || price.scale() > 2) {
                throw new OrderBizException(OrderErrorCode.COURSE_NOT_ON_SALE,
                        "课程价格数据异常，无法下单: " + course.getId());
            }
            totalAmount = totalAmount.add(price);

            TradeOrderItemEntity itemEntity = TradeOrderItemEntity.builder()
                    .id(identifierGenerator.nextId())
                    .orderId(orderId)
                    .courseId(course.getId())
                    .courseTitleSnapshot(course.getTitle())
                    .coverFileIdSnapshot(course.getCoverFileId())
                    .unitPrice(price)
                    .quantity(1)
                    .lineAmount(price)
                    .refundReservedAmount(BigDecimal.ZERO)
                    .refundedAmount(BigDecimal.ZERO)
                    .fulfillmentStatus(FulfillmentStatus.UNFULFILLED.name())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            orderItemEntities.add(itemEntity);
        }

        String hash = sha256(token != null ? token : orderNo);

        TradeOrderEntity orderEntity = TradeOrderEntity.builder()
                .id(orderId)
                .orderNo(orderNo)
                .studentId(studentId)
                .status(OrderStatus.PENDING_PAYMENT.name())
                .originalAmount(totalAmount)
                .payableAmount(totalAmount)
                .currency("CNY")
                .expiresAt(expiresAt)
                .paidAt(null)
                .cancelledAt(null)
                .idempotencyKeyHash(hash)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // BUG-019 修复：DB 写入（订单 + 明细 + 清购物车）收敛到短事务。
        transactionTemplate.executeWithoutResult(status -> {
            tradeOrderMapper.insert(orderEntity);
            for (TradeOrderItemEntity itemEntity : orderItemEntities) {
                tradeOrderItemMapper.insert(itemEntity);
            }
            if (isCartBuy) {
                cartService.clearCart(studentId, true);
            }
        });

        // BUG-018 修复：延时关单消息在事务提交后发送，消除事务回滚产生的
        // 幽灵订单消息；发送失败由 OrderDelayProducer 记日志、
        // ExpiredOrderSweeper 定时兑底关单，不再阻塞下单。
        OrderDelayProducer delayProducer = orderDelayProducerProvider.getIfAvailable();
        if (delayProducer != null) {
            delayProducer.sendDelayMessage(OrderDelayMessage.builder()
                    .orderId(orderId)
                    .orderNo(orderNo)
                    .createdAt(now)
                    .build());
        }

        return toDetailResponse(orderEntity, orderItemEntities);
    }

    @Override
    public OrderDetailResponse getOrderDetail(Long studentId, Long orderId) {
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getStudentId(), studentId)) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_OWNED);
        }
        List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, orderId));
        return toDetailResponse(order, items);
    }

    @Override
    public PageResponse<OrderDetailResponse> listStudentOrders(Long studentId, String status, int page, int size) {
        Page<TradeOrderEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TradeOrderEntity> query = new LambdaQueryWrapper<TradeOrderEntity>()
                .eq(TradeOrderEntity::getStudentId, studentId)
                .orderByDesc(TradeOrderEntity::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(TradeOrderEntity::getStatus, status.trim());
        }

        Page<TradeOrderEntity> resultPage = tradeOrderMapper.selectPage(pageParam, query);
        List<TradeOrderEntity> records = resultPage.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResponse.of(List.of(), page, size, resultPage.getTotal());
        }

        List<OrderDetailResponse> responseList = new ArrayList<>(records.size());
        for (TradeOrderEntity record : records) {
            List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                    new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, record.getId()));
            responseList.add(toDetailResponse(record, items));
        }

        return PageResponse.of(responseList, page, size, resultPage.getTotal());
    }

    @Override
    @Transactional
    public void cancelOrder(Long studentId, Long orderId) {
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getStudentId(), studentId)) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_OWNED);
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new OrderBizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        int rows = tradeOrderMapper.updateStatusToCancelledWithCas(
                orderId, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.CANCELLED.name(), LocalDateTime.now());
        if (rows == 0) {
            throw new OrderBizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
    }

    @Override
    @Transactional
    public OrderDetailResponse mockPay(Long studentId, Long orderId) {
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getStudentId(), studentId)) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_OWNED);
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new OrderBizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        // BUG-016 修复：过期订单拒绝支付（延时关单消息丢失/MQ 积压时，支付侧
        // 校验与 CAS SQL 的 expires_at 条件互为双保险，不再单点依赖关单消息）。
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OrderBizException(OrderErrorCode.ORDER_EXPIRED);
        }

        LocalDateTime paidAt = LocalDateTime.now();
        int rows = tradeOrderMapper.updateStatusToPaidWithCas(
                orderId, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(), paidAt);
        if (rows == 0) {
            throw new OrderBizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }

        List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, orderId));

        List<Long> courseIds = (items != null) ? items.stream()
                .map(TradeOrderItemEntity::getCourseId)
                .toList() : List.of();

        order.setStatus(OrderStatus.PAID.name());
        order.setPaidAt(paidAt);

        OrderPaidEvent event = OrderPaidEvent.builder()
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .studentId(studentId)
                .courseIds(courseIds)
                .paidAmount(order.getPayableAmount())
                .paidAt(paidAt)
                .build();

        // BUG-017 修复：已支付事件写入 outbox（与置 PAID 同事务提交），由
        // OutboxRelay 提交后异步投递 MQ——发送失败不再被吞，付款后必开课。
        // aggregateVersion 为 CAS 递增后的聚合版本（version+1）。
        outboxEventWriter.appendOrderPaid(event, order.getVersion() + 1L);

        return toDetailResponse(order, items);
    }

    @Override
    public OrderPayableSnapshotResponse getPayableSnapshot(Long orderId) {
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, orderId));
        List<OrderItemResponse> itemResponses = (items != null) ? items.stream()
                .map(this::toItemResponse)
                .toList() : List.of();

        return OrderPayableSnapshotResponse.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .studentId(order.getStudentId())
                .status(order.getStatus())
                .payableAmount(order.getPayableAmount())
                .currency(order.getCurrency())
                .expiresAt(order.getExpiresAt())
                .items(itemResponses)
                .build();
    }

    @Override
    public OrderFulfillmentSnapshotResponse getFulfillmentSnapshot(Long orderId) {
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, orderId));
        List<OrderItemResponse> itemResponses = (items != null) ? items.stream()
                .map(this::toItemResponse)
                .toList() : List.of();

        return OrderFulfillmentSnapshotResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .aggregateVersion(order.getVersion())
                .items(itemResponses)
                .build();
    }

    @Override
    public PageResponse<OrderDetailResponse> listAdminOrders(String orderNo, String status, int page, int size) {
        Page<TradeOrderEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TradeOrderEntity> query = new LambdaQueryWrapper<>();
        if (orderNo != null && !orderNo.isBlank()) {
            query.eq(TradeOrderEntity::getOrderNo, orderNo.trim());
        }
        if (status != null && !status.isBlank()) {
            query.eq(TradeOrderEntity::getStatus, status.trim());
        }
        query.orderByDesc(TradeOrderEntity::getCreatedAt);

        Page<TradeOrderEntity> resultPage = tradeOrderMapper.selectPage(pageParam, query);
        List<TradeOrderEntity> records = resultPage.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResponse.of(List.of(), page, size, resultPage.getTotal());
        }

        List<OrderDetailResponse> responseList = new ArrayList<>(records.size());
        for (TradeOrderEntity record : records) {
            List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                    new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, record.getId()));
            responseList.add(toDetailResponse(record, items));
        }

        return PageResponse.of(responseList, page, size, resultPage.getTotal());
    }

    @Override
    public OrderDetailResponse getAdminOrderDetail(Long orderId) {
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, orderId));
        return toDetailResponse(order, items);
    }

    private CourseSalesSnapshotDto fetchAndValidateCourse(Long courseId) {
        ApiResponse<CourseSalesSnapshotDto> response;
        try {
            response = courseClient.getCourseDetail(courseId);
        } catch (Exception ex) {
            // 排查日志：保留真实远端异常，避免 COURSE_NOT_ON_SALE 掩盖网络/反序列化问题
            log.warn("获取课程详情失败，courseId={}", courseId, ex);
            throw new OrderBizException(OrderErrorCode.COURSE_NOT_ON_SALE, "无法获取课程详情", null, ex);
        }
        if (response == null || response.data() == null) {
            throw new OrderBizException(OrderErrorCode.COURSE_NOT_ON_SALE);
        }
        CourseSalesSnapshotDto dto = response.data();
        if (!dto.isPurchasable()) {
            throw new OrderBizException(OrderErrorCode.COURSE_NOT_ON_SALE);
        }
        if (Boolean.TRUE.equals(dto.getEnrolled())) {
            throw new OrderBizException(OrderErrorCode.COURSE_ALREADY_ENROLLED);
        }
        return dto;
    }

    private OrderDetailResponse toDetailResponse(TradeOrderEntity order, List<TradeOrderItemEntity> items) {
        List<OrderItemResponse> itemResponses = (items != null) ? items.stream()
                .map(this::toItemResponse)
                .toList() : List.of();

        long countdown = 0L;
        if (OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus()) && order.getExpiresAt() != null) {
            Duration diff = Duration.between(LocalDateTime.now(), order.getExpiresAt());
            countdown = Math.max(0, diff.getSeconds());
        }

        return OrderDetailResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .studentId(order.getStudentId())
                .status(order.getStatus())
                .originalAmount(order.getOriginalAmount())
                .payableAmount(order.getPayableAmount())
                .currency(order.getCurrency())
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .cancelledAt(order.getCancelledAt())
                .items(itemResponses)
                .countdownSeconds(countdown)
                .build();
    }

    private OrderItemResponse toItemResponse(TradeOrderItemEntity entity) {
        return OrderItemResponse.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .courseId(entity.getCourseId())
                .courseTitleSnapshot(entity.getCourseTitleSnapshot())
                .coverFileIdSnapshot(entity.getCoverFileIdSnapshot())
                .unitPrice(entity.getUnitPrice())
                .quantity(entity.getQuantity())
                .lineAmount(entity.getLineAmount())
                .fulfillmentStatus(entity.getFulfillmentStatus())
                .build();
    }

    @Override
    @Transactional
    public void processPaymentSuccess(Long orderId, Long paymentOrderId, Long userId, Long amountCents, LocalDateTime paidAt) {
        if (orderId == null) {
            return;
        }
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("Order {} not found when processing payment success event", orderId);
            return;
        }
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            log.info("Order {} is already in PAID status, ignoring duplicate payment event", orderId);
            return;
        }

        LocalDateTime actualPaidAt = paidAt != null ? paidAt : LocalDateTime.now();
        int rows = tradeOrderMapper.updateStatusToPaidWithCas(
                orderId, OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(), actualPaidAt);
        if (rows == 0) {
            // 幂等：订单已是 PAID 属正常重复事件
            TradeOrderEntity latest = tradeOrderMapper.selectById(orderId);
            if (latest != null && OrderStatus.PAID.name().equals(latest.getStatus())) {
                log.info("Order {} is already PAID, ignoring duplicate payment success event", orderId);
                return;
            }
            // 非幂等失败：渠道已扣款但订单已关单/取消/过期，履约不会发生 → 资损风险，必须告警并由对账/人工退款介入
            log.error("PAYMENT-ORDER-MISMATCH: payment succeeded (paymentOrderId={}) but order {} is in status {} (expiresAt={}); "
                            + "fulfillment will not proceed and no automatic refund exists - manual refund/reconciliation required",
                    paymentOrderId, orderId, latest != null ? latest.getStatus() : "UNKNOWN",
                    latest != null ? latest.getExpiresAt() : null);
            return;
        }

        List<TradeOrderItemEntity> items = tradeOrderItemMapper.selectList(
                new LambdaQueryWrapper<TradeOrderItemEntity>().eq(TradeOrderItemEntity::getOrderId, orderId));

        List<Long> courseIds = (items != null) ? items.stream()
                .map(TradeOrderItemEntity::getCourseId)
                .toList() : List.of();

        OrderPaidEvent event = OrderPaidEvent.builder()
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .studentId(order.getStudentId())
                .courseIds(courseIds)
                .paidAmount(order.getPayableAmount())
                .paidAt(actualPaidAt)
                .build();

        outboxEventWriter.appendOrderPaid(event, order.getVersion() + 1L);
        log.info("Successfully updated order {} to PAID and appended OrderPaidEvent to outbox", orderId);
    }

    @Override
    @Transactional
    public void processPaymentRefund(Long orderId, Long refundId, Long refundAmountCents, LocalDateTime refundedAt) {
        if (orderId == null) {
            return;
        }
        TradeOrderEntity order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("Order {} not found when processing payment refund event", orderId);
            return;
        }
        if (OrderStatus.REFUNDED.name().equals(order.getStatus())) {
            log.info("Order {} is already in REFUNDED status, ignoring", orderId);
            return;
        }

        int rows = tradeOrderMapper.updateStatusWithCas(
                orderId, OrderStatus.PAID.name(), OrderStatus.REFUNDED.name());
        if (rows > 0) {
            log.info("Successfully updated order {} status to REFUNDED from payment refund event", orderId);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
