package com.educloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.PageResponse;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.order.dto.request.OrderCreateRequest;
import com.educloud.order.dto.response.OrderDetailResponse;
import com.educloud.order.dto.response.OrderItemResponse;
import com.educloud.order.entity.*;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.feign.CourseClient;
import com.educloud.order.feign.dto.CourseSalesSnapshotDto;
import com.educloud.order.mapper.CartItemMapper;
import com.educloud.order.mapper.TradeOrderItemMapper;
import com.educloud.order.mapper.TradeOrderMapper;
import com.educloud.order.service.CartService;
import com.educloud.order.service.IdempotencyService;
import com.educloud.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public OrderDetailResponse createOrder(Long studentId, OrderCreateRequest request, String headerIdempotencyToken) {
        String token = (headerIdempotencyToken != null && !headerIdempotencyToken.isBlank())
                ? headerIdempotencyToken
                : (request != null ? request.getIdempotencyToken() : null);

        idempotencyService.validateAndConsume(studentId, token);

        List<CourseSalesSnapshotDto> coursesToBuy = new ArrayList<>();
        boolean isCartBuy = (request == null || request.getCourseId() == null);

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
            cartService.clearCart(studentId, true);
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
            BigDecimal price = course.getPrice() != null ? course.getPrice() : BigDecimal.ZERO;
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

        tradeOrderMapper.insert(orderEntity);
        for (TradeOrderItemEntity item : orderItemEntities) {
            tradeOrderItemMapper.insert(item);
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

    private CourseSalesSnapshotDto fetchAndValidateCourse(Long courseId) {
        ApiResponse<CourseSalesSnapshotDto> response;
        try {
            response = courseClient.getCourseDetail(courseId);
        } catch (Exception ex) {
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
