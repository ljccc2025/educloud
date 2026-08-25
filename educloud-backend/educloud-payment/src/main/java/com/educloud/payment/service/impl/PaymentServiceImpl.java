package com.educloud.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.payment.config.PaymentProperties;
import com.educloud.payment.dto.request.CashierPayRequest;
import com.educloud.payment.dto.response.CashierPayResponse;
import com.educloud.payment.dto.response.PaymentDetailResponse;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.PaymentTransactionEntity;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.TradeType;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import com.educloud.payment.feign.OrderClient;
import com.educloud.payment.feign.dto.OrderPayableSnapshotResponse;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.PaymentTransactionMapper;
import com.educloud.payment.messaging.OutboxEventWriter;
import com.educloud.payment.messaging.events.PaymentSucceededEvent;
import com.educloud.payment.service.PaymentService;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.PaymentContext;
import com.educloud.payment.spi.model.UnifiedPayResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentChannelFactory channelFactory;
    private final OutboxEventWriter outboxEventWriter;
    private final OrderClient orderClient;
    private final PaymentProperties properties;

    @Override
    @Transactional
    public CashierPayResponse createCashierPayment(Long userId, CashierPayRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getOrderId(), "orderId");
        Objects.requireNonNull(request.getChannelCode(), "channelCode");

        // 安全修复（M08 审查）：生产环境拒绝 MOCK 渠道下单，与 mockConfirmPayment
        // 及 MOCK 回调门控对齐，堵住“MOCK 渠道零成本置支付成功”资损链。
        if (request.getChannelCode() == PaymentChannel.MOCK && isProduction(properties.environment())) {
            throw new PaymentBizException(PaymentErrorCode.MOCK_PAY_DISABLED, "生产环境禁用 Mock 支付渠道");
        }

        String internalSecret = properties.internal() != null ? properties.internal().secretToken() : null;
        ApiResponse<OrderPayableSnapshotResponse> orderSnapshotResp;
        try {
            orderSnapshotResp = orderClient.getPayableSnapshot(request.getOrderId(), internalSecret);
        } catch (Exception e) {
            log.error("Failed to query order payable snapshot from educloud-order for orderId={}: {}",
                    request.getOrderId(), e.getMessage());
            throw new PaymentBizException(PaymentErrorCode.CHANNEL_INVOKE_FAILED, "无法获取订单可付信息: " + e.getMessage());
        }

        if (orderSnapshotResp == null || orderSnapshotResp.data() == null) {
            throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "订单不存在或无法获取快照");
        }

        OrderPayableSnapshotResponse orderSnapshot = orderSnapshotResp.data();
        if (!"PENDING_PAYMENT".equalsIgnoreCase(orderSnapshot.getStatus())) {
            throw new PaymentBizException(PaymentErrorCode.PAYMENT_STATUS_INVALID, "订单状态为 " + orderSnapshot.getStatus() + "，不可发起支付");
        }

        if (orderSnapshot.getExpiresAt() != null && orderSnapshot.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PaymentBizException(PaymentErrorCode.PAYMENT_EXPIRED, "订单已超时关闭");
        }

        if (userId != null && orderSnapshot.getStudentId() != null && !userId.equals(orderSnapshot.getStudentId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "无权支付他人订单");
        }

        // 金额修复（M08 审查）：元→分四舍五入后精确取整，避免 longValue() 静默截断。
        long amountCents = orderSnapshot.getPayableAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        LambdaQueryWrapper<PaymentOrderEntity> orderQuery = new LambdaQueryWrapper<PaymentOrderEntity>()
                .eq(PaymentOrderEntity::getOrderId, request.getOrderId())
                .eq(PaymentOrderEntity::getDeleted, 0)
                .last("LIMIT 1");
        PaymentOrderEntity paymentOrder = paymentOrderMapper.selectOne(orderQuery);

        if (paymentOrder == null) {
            PaymentOrderEntity candidate = PaymentOrderEntity.builder()
                    .id(IdWorker.getId())
                    .orderId(request.getOrderId())
                    .userId(userId != null ? userId : orderSnapshot.getStudentId())
                    .amountCents(amountCents)
                    .currency(orderSnapshot.getCurrency() != null ? orderSnapshot.getCurrency() : "CNY")
                    .channelCode(request.getChannelCode())
                    .tradeType(request.getTradeType() != null ? request.getTradeType() : TradeType.NATIVE)
                    .status(PaymentStatus.PAYING)
                    .expiresAt(orderSnapshot.getExpiresAt() != null ? orderSnapshot.getExpiresAt() : LocalDateTime.now().plusMinutes(15))
                    .version(0)
                    .deleted(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            try {
                paymentOrderMapper.insert(candidate);
                paymentOrder = candidate;
            } catch (DuplicateKeyException dup) {
                // 并发提单冲突（uk_order_id）：复用既有支付单，避免同一订单双支付单重复扣款。
                paymentOrder = paymentOrderMapper.selectOne(orderQuery);
                if (paymentOrder == null) {
                    throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单创建并发冲突，请重试");
                }
            }
        }

        if (paymentOrder.getStatus() == PaymentStatus.SUCCESS) {
            throw new PaymentBizException(PaymentErrorCode.DUPLICATE_PAYMENT, "该订单已支付成功，请勿重复支付");
        }
        paymentOrder.setChannelCode(request.getChannelCode());
        paymentOrder.setTradeType(request.getTradeType() != null ? request.getTradeType() : TradeType.NATIVE);
        paymentOrder.setStatus(PaymentStatus.PAYING);
        paymentOrder.setAmountCents(amountCents);
        if (orderSnapshot.getExpiresAt() != null) {
            paymentOrder.setExpiresAt(orderSnapshot.getExpiresAt());
        }

        PaymentChannelPlugin plugin = channelFactory.getPlugin(request.getChannelCode());
        PaymentContext context = PaymentContext.builder()
                .paymentOrderId(paymentOrder.getId())
                .orderId(paymentOrder.getOrderId())
                .userId(paymentOrder.getUserId())
                .amountCents(paymentOrder.getAmountCents())
                .currency(paymentOrder.getCurrency())
                .channel(paymentOrder.getChannelCode())
                .tradeType(paymentOrder.getTradeType())
                .subject(request.getSubject() != null ? request.getSubject() : "EduCloud 课程订单: " + orderSnapshot.getOrderNo())
                .expiresAt(paymentOrder.getExpiresAt())
                .clientIp(request.getClientIp())
                .build();

        UnifiedPayResult payResult = plugin.initiatePayment(context);
        if (!payResult.isSuccess()) {
            throw new PaymentBizException(PaymentErrorCode.CHANNEL_INVOKE_FAILED, "调用渠道下单失败: " + payResult.getErrorMessage());
        }

        paymentOrder.setPayUrl(payResult.getPayUrl());
        paymentOrder.setQrCode(payResult.getQrCode());
        paymentOrder.setChannelTradeNo(payResult.getChannelTradeNo());
        paymentOrder.setUpdatedAt(LocalDateTime.now());
        paymentOrderMapper.updateById(paymentOrder);

        PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .id(IdWorker.getId())
                .paymentOrderId(paymentOrder.getId())
                .transactionNo("TX_" + paymentOrder.getId() + "_" + System.currentTimeMillis())
                .channelCode(paymentOrder.getChannelCode())
                .actionType("PAY")
                .amountCents(paymentOrder.getAmountCents())
                .feeCents(0L)
                .rawRequest(context.toString())
                .rawResponse(payResult.getRawResponse())
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();
        paymentTransactionMapper.insert(transaction);

        return CashierPayResponse.builder()
                .paymentOrderId(paymentOrder.getId())
                .orderId(paymentOrder.getOrderId())
                .channelCode(paymentOrder.getChannelCode())
                .amountCents(paymentOrder.getAmountCents())
                .currency(paymentOrder.getCurrency())
                .payUrl(paymentOrder.getPayUrl())
                .qrCode(paymentOrder.getQrCode())
                .status(paymentOrder.getStatus())
                .expiresAt(paymentOrder.getExpiresAt())
                .build();
    }

    @Override
    public PaymentDetailResponse getPaymentDetail(Long userId, Long paymentOrderId) {
        PaymentOrderEntity paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
        if (paymentOrder == null || paymentOrder.getDeleted() == 1) {
            throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }

        if (userId != null && paymentOrder.getUserId() != null && !userId.equals(paymentOrder.getUserId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "无权查看他人支付单");
        }

        return toDetailResponse(paymentOrder);
    }

    @Override
    @Transactional
    public PaymentDetailResponse mockConfirmPayment(Long userId, Long paymentOrderId) {
        String env = properties.environment();
        if ("prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env)) {
            throw new PaymentBizException(PaymentErrorCode.MOCK_PAY_DISABLED, "生产环境下禁用 Mock 模拟支付");
        }

        PaymentOrderEntity paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
        if (paymentOrder == null || paymentOrder.getDeleted() == 1) {
            throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }

        if (userId != null && paymentOrder.getUserId() != null && !userId.equals(paymentOrder.getUserId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "无权操作他人支付单");
        }

        if (paymentOrder.getChannelCode() != PaymentChannel.MOCK) {
            throw new PaymentBizException(PaymentErrorCode.PAYMENT_STATUS_INVALID, "非 Mock 渠道支付单无法通过该接口模拟支付");
        }

        if (paymentOrder.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PaymentBizException(PaymentErrorCode.PAYMENT_EXPIRED, "支付单已超时关闭");
        }

        LocalDateTime now = LocalDateTime.now();
        String channelTradeNo = "MOCK_TR_CONFIRM_" + paymentOrderId;
        int updated = paymentOrderMapper.updateStatusToSuccessCas(
                paymentOrderId, PaymentStatus.SUCCESS, now, channelTradeNo, now);

        if (updated == 0) {
            throw new PaymentBizException(PaymentErrorCode.DUPLICATE_PAYMENT, "支付单状态已变更或已过期");
        }

        paymentOrder.setStatus(PaymentStatus.SUCCESS);
        paymentOrder.setPaidAt(now);
        paymentOrder.setChannelTradeNo(channelTradeNo);

        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                .paymentOrderId(paymentOrder.getId())
                .orderId(paymentOrder.getOrderId())
                .userId(paymentOrder.getUserId())
                .amountCents(paymentOrder.getAmountCents())
                .channelCode(paymentOrder.getChannelCode())
                .channelTradeNo(channelTradeNo)
                .paidAt(now)
                .build();

        outboxEventWriter.writeEvent("PAYMENT", paymentOrder.getId(), "PaymentSucceededEvent", event);

        PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .id(IdWorker.getId())
                .paymentOrderId(paymentOrder.getId())
                .transactionNo("TX_MOCK_CONFIRM_" + paymentOrderId)
                .channelCode(PaymentChannel.MOCK)
                .actionType("MOCK_CONFIRM")
                .amountCents(paymentOrder.getAmountCents())
                .feeCents(0L)
                .rawRequest("mockConfirmPayment")
                .rawResponse("SUCCESS")
                .status("SUCCESS")
                .createdAt(now)
                .build();
        paymentTransactionMapper.insert(transaction);

        return toDetailResponse(paymentOrder);
    }

    @Override
    public PaymentOrderEntity getById(Long paymentOrderId) {
        return paymentOrderMapper.selectById(paymentOrderId);
    }

    private static boolean isProduction(String env) {
        return "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env);
    }

    private PaymentDetailResponse toDetailResponse(PaymentOrderEntity entity) {
        return PaymentDetailResponse.builder()
                .paymentOrderId(entity.getId())
                .orderId(entity.getOrderId())
                .userId(entity.getUserId())
                .amountCents(entity.getAmountCents())
                .currency(entity.getCurrency())
                .channelCode(entity.getChannelCode())
                .tradeType(entity.getTradeType())
                .status(entity.getStatus())
                .channelTradeNo(entity.getChannelTradeNo())
                .payUrl(entity.getPayUrl())
                .qrCode(entity.getQrCode())
                .expiresAt(entity.getExpiresAt())
                .paidAt(entity.getPaidAt())
                .build();
    }
}
