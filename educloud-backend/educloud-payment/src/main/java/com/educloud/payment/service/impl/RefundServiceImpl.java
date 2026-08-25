package com.educloud.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.payment.dto.request.RefundApplyRequest;
import com.educloud.payment.dto.request.RefundAuditRequest;
import com.educloud.payment.dto.response.RefundDetailResponse;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.PaymentRefundEntity;
import com.educloud.payment.entity.PaymentTransactionEntity;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.RefundStatus;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.PaymentRefundMapper;
import com.educloud.payment.mapper.PaymentTransactionMapper;
import com.educloud.payment.messaging.OutboxEventWriter;
import com.educloud.payment.messaging.events.PaymentRefundedEvent;
import com.educloud.payment.service.RefundService;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.RefundContext;
import com.educloud.payment.spi.model.UnifiedRefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final PaymentRefundMapper refundMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper transactionMapper;
    private final PaymentChannelFactory channelFactory;
    private final OutboxEventWriter outboxEventWriter;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public RefundDetailResponse applyRefund(Long userId, RefundApplyRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getOrderId(), "orderId");
        Objects.requireNonNull(request.getRefundAmountCents(), "refundAmountCents");

        // 并发修复（M08 审查）：支付单行级 FOR UPDATE，把“累计已退 + 本次 ≤ 支付金额”
        // 的 check-then-insert 串行化，杜绝并发申请突破可退上限。
        PaymentOrderEntity paymentOrder = null;
        if (request.getPaymentOrderId() != null) {
            paymentOrder = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrderEntity>()
                            .eq(PaymentOrderEntity::getId, request.getPaymentOrderId())
                            .eq(PaymentOrderEntity::getDeleted, 0)
                            .last("FOR UPDATE"));
        }
        if (paymentOrder == null) {
            paymentOrder = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrderEntity>()
                            .eq(PaymentOrderEntity::getOrderId, request.getOrderId())
                            .eq(PaymentOrderEntity::getDeleted, 0)
                            .orderByDesc(PaymentOrderEntity::getId)
                            .last("LIMIT 1 FOR UPDATE"));
        }

        if (paymentOrder == null || paymentOrder.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentBizException(PaymentErrorCode.REFUND_NOT_ALLOWED, "未找到已成功支付的订单，无法申请退款");
        }

        if (userId != null && paymentOrder.getUserId() != null && !userId.equals(paymentOrder.getUserId())) {
            throw new com.educloud.common.error.BusinessException(
                    com.educloud.common.error.CommonErrorCode.ACCESS_DENIED, "无权对他人订单申请退款");
        }

        // 幂等修复（M08 审查）：同一 refundRequestId 重试直接返回既有退款单，不重复建单。
        if (request.getRefundRequestId() != null) {
            PaymentRefundEntity existing = refundMapper.selectOne(
                    new LambdaQueryWrapper<PaymentRefundEntity>()
                            .eq(PaymentRefundEntity::getPaymentOrderId, paymentOrder.getId())
                            .eq(PaymentRefundEntity::getRefundRequestId, request.getRefundRequestId())
                            .last("LIMIT 1"));
            if (existing != null) {
                log.info("Idempotent refund apply: refundRequestId={} already applied as refund {}",
                        request.getRefundRequestId(), existing.getId());
                return toDetailResponse(existing);
            }
        }

        List<PaymentRefundEntity> existingRefunds = refundMapper.selectList(
                new LambdaQueryWrapper<PaymentRefundEntity>()
                        .eq(PaymentRefundEntity::getPaymentOrderId, paymentOrder.getId())
                        .in(PaymentRefundEntity::getStatus, List.of(RefundStatus.APPLIED, RefundStatus.PROCESSING, RefundStatus.SUCCESS)));

        long totalExistingRefundAmount = existingRefunds.stream()
                .mapToLong(PaymentRefundEntity::getRefundAmountCents)
                .sum();

        if (totalExistingRefundAmount + request.getRefundAmountCents() > paymentOrder.getAmountCents()) {
            throw new PaymentBizException(PaymentErrorCode.REFUND_AMOUNT_EXCEEDED,
                    String.format("申请退款金额超过最大可退余额: maxAvailable=%d, requested=%d",
                            paymentOrder.getAmountCents() - totalExistingRefundAmount, request.getRefundAmountCents()));
        }

        PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .id(IdWorker.getId())
                .paymentOrderId(paymentOrder.getId())
                .orderId(paymentOrder.getOrderId())
                .refundRequestId(request.getRefundRequestId())
                .refundAmountCents(request.getRefundAmountCents())
                .currency(paymentOrder.getCurrency())
                .reason(request.getReason())
                .channelCode(paymentOrder.getChannelCode())
                .status(RefundStatus.APPLIED)
                .version(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        refundMapper.insert(refund);
        log.info("Refund applied: id={}, paymentOrderId={}, amountCents={}",
                refund.getId(), paymentOrder.getId(), request.getRefundAmountCents());

        return toDetailResponse(refund);
    }

    @Override
    public RefundDetailResponse auditRefund(Long adminUserId, Long refundId, RefundAuditRequest request) {
        Objects.requireNonNull(refundId, "refundId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getApprove(), "approve");

        // 事务修复（M08 审查）：原实现在同一 @Transactional 内调用渠道退款，
        // “渠道已成功但提交失败”会导致重复退款；且长事务内挂外部 IO 占用连接。
        // 现拆三段：①短事务审核决策与状态跃迁；②事务外调渠道；③短事务收敛与事件出箱。
        AuditDecision decision = transactionTemplate.execute(status -> {
            PaymentRefundEntity refund = refundMapper.selectById(refundId);
            if (refund == null) {
                throw new PaymentBizException(PaymentErrorCode.REFUND_NOT_FOUND, "退款申请不存在");
            }
            if (refund.getStatus() != RefundStatus.APPLIED) {
                throw new PaymentBizException(PaymentErrorCode.REFUND_STATUS_INVALID,
                        "当前退款单状态为 " + refund.getStatus() + "，不支持审核");
            }

            LocalDateTime now = LocalDateTime.now();
            refund.setAuditedBy(adminUserId);
            refund.setAuditedAt(now);
            refund.setAuditRemark(request.getRemark());
            refund.setUpdatedAt(now);

            if (!request.getApprove()) {
                refund.setStatus(RefundStatus.REJECTED);
                refundMapper.updateById(refund);
                log.info("Refund {} rejected by admin {}", refundId, adminUserId);
                return new AuditDecision(refund, null, true);
            }

            // 审核通过前加锁复验累计可退余额，防止申请/审核并发导致超额退款。
            PaymentOrderEntity paymentOrder = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrderEntity>()
                            .eq(PaymentOrderEntity::getId, refund.getPaymentOrderId())
                            .eq(PaymentOrderEntity::getDeleted, 0)
                            .last("FOR UPDATE"));
            if (paymentOrder == null) {
                throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "关联的支付单不存在");
            }

            long totalOtherRefunds = refundMapper.selectList(
                            new LambdaQueryWrapper<PaymentRefundEntity>()
                                    .eq(PaymentRefundEntity::getPaymentOrderId, paymentOrder.getId())
                                    .ne(PaymentRefundEntity::getId, refund.getId())
                                    .in(PaymentRefundEntity::getStatus,
                                            List.of(RefundStatus.APPLIED, RefundStatus.PROCESSING, RefundStatus.SUCCESS)))
                    .stream()
                    .mapToLong(PaymentRefundEntity::getRefundAmountCents)
                    .sum();
            if (totalOtherRefunds + refund.getRefundAmountCents() > paymentOrder.getAmountCents()) {
                throw new PaymentBizException(PaymentErrorCode.REFUND_AMOUNT_EXCEEDED,
                        String.format("审核时累计退款超出可退余额: maxAvailable=%d, requested=%d",
                                paymentOrder.getAmountCents() - totalOtherRefunds, refund.getRefundAmountCents()));
            }

            refund.setStatus(RefundStatus.PROCESSING);
            refundMapper.updateById(refund);
            return new AuditDecision(refund, paymentOrder, false);
        });

        if (decision == null) {
            throw new PaymentBizException(PaymentErrorCode.REFUND_STATUS_INVALID, "退款审核事务未执行");
        }
        if (decision.rejected()) {
            return toDetailResponse(decision.refund());
        }

        PaymentRefundEntity refund = decision.refund();
        PaymentOrderEntity paymentOrder = decision.paymentOrder();

        // 阶段二（事务外）：调用渠道退款。
        RefundContext context = RefundContext.builder()
                .refundId(refund.getId())
                .paymentOrderId(paymentOrder.getId())
                .orderId(paymentOrder.getOrderId())
                .channelTradeNo(paymentOrder.getChannelTradeNo())
                .totalAmountCents(paymentOrder.getAmountCents())
                .refundAmountCents(refund.getRefundAmountCents())
                .currency(refund.getCurrency())
                .reason(refund.getReason())
                .channel(refund.getChannelCode())
                .build();

        PaymentChannelPlugin plugin = channelFactory.getPlugin(refund.getChannelCode());
        UnifiedRefundResult refundResult = plugin.initiateRefund(context);

        // 阶段三（短事务）：状态收敛 + 事件出箱 + 流水。
        if (refundResult.isSuccess()) {
            String channelRefundNo = refundResult.getChannelRefundNo() != null
                    ? refundResult.getChannelRefundNo()
                    : "REF_" + refund.getId();
            LocalDateTime refundedAt = refundResult.getRefundedAt() != null ? refundResult.getRefundedAt() : LocalDateTime.now();

            Boolean finalized = transactionTemplate.execute(status -> {
                int updated = refundMapper.updateStatusCas(
                        refund.getId(), RefundStatus.PROCESSING, RefundStatus.SUCCESS, refundedAt, channelRefundNo);
                if (updated == 0) {
                    return false;
                }

                PaymentRefundedEvent event = PaymentRefundedEvent.builder()
                        .refundId(refund.getId())
                        .paymentOrderId(refund.getPaymentOrderId())
                        .orderId(refund.getOrderId())
                        .userId(paymentOrder.getUserId())
                        .refundAmountCents(refund.getRefundAmountCents())
                        .refundedAt(refundedAt)
                        .build();

                outboxEventWriter.writeEvent("REFUND", refund.getId(), "PaymentRefundedEvent", event);

                PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                        .id(IdWorker.getId())
                        .paymentOrderId(paymentOrder.getId())
                        .transactionNo("TX_REF_" + refund.getId())
                        .channelCode(refund.getChannelCode())
                        .actionType("REFUND")
                        .amountCents(refund.getRefundAmountCents())
                        .feeCents(0L)
                        .rawRequest(context.toString())
                        .rawResponse(refundResult.getRawResponse())
                        .status("SUCCESS")
                        .createdAt(LocalDateTime.now())
                        .build();
                transactionMapper.insert(transaction);
                return true;
            });

            if (Boolean.TRUE.equals(finalized)) {
                refund.setStatus(RefundStatus.SUCCESS);
                refund.setRefundedAt(refundedAt);
                refund.setChannelRefundNo(channelRefundNo);
                log.info("Refund {} successfully completed and broadcasted", refundId);
            }
        } else {
            transactionTemplate.executeWithoutResult(status -> {
                refund.setStatus(RefundStatus.FAILED);
                refundMapper.updateById(refund);
            });
            log.error("Refund {} invocation failed: {}", refundId, refundResult.getErrorMessage());
        }

        return toDetailResponse(refund);
    }

    /** 审核阶段一的决策结果：退款单 + 加锁读到的支付单 + 是否驳回。 */
    private record AuditDecision(PaymentRefundEntity refund, PaymentOrderEntity paymentOrder, boolean rejected) {
    }

    @Override
    public RefundDetailResponse getRefundDetail(Long refundId) {
        PaymentRefundEntity refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new PaymentBizException(PaymentErrorCode.REFUND_NOT_FOUND, "退款单不存在");
        }
        return toDetailResponse(refund);
    }

    @Override
    public PageResponse<RefundDetailResponse> listRefunds(String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<PaymentRefundEntity> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<PaymentRefundEntity> query = new LambdaQueryWrapper<PaymentRefundEntity>()
                .orderByDesc(PaymentRefundEntity::getId);

        if (status != null && !status.isBlank()) {
            try {
                RefundStatus refundStatus = RefundStatus.valueOf(status.trim().toUpperCase());
                query.eq(PaymentRefundEntity::getStatus, refundStatus);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid refund status query parameter: {}", status);
                return PageResponse.of(List.of(), safePage, safeSize, 0);
            }
        }

        Page<PaymentRefundEntity> resultPage = refundMapper.selectPage(pageParam, query);
        List<RefundDetailResponse> items = resultPage.getRecords().stream()
                .map(this::toDetailResponse)
                .toList();

        return PageResponse.of(items, safePage, safeSize, resultPage.getTotal());
    }

    private RefundDetailResponse toDetailResponse(PaymentRefundEntity entity) {
        return RefundDetailResponse.builder()
                .refundId(entity.getId())
                .paymentOrderId(entity.getPaymentOrderId())
                .orderId(entity.getOrderId())
                .refundRequestId(entity.getRefundRequestId())
                .refundAmountCents(entity.getRefundAmountCents())
                .currency(entity.getCurrency())
                .reason(entity.getReason())
                .channelCode(entity.getChannelCode())
                .channelRefundNo(entity.getChannelRefundNo())
                .status(entity.getStatus())
                .auditedBy(entity.getAuditedBy())
                .auditedAt(entity.getAuditedAt())
                .auditRemark(entity.getAuditRemark())
                .refundedAt(entity.getRefundedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
