package com.educloud.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.common.api.ApiResponse;
import com.educloud.payment.config.PaymentProperties;
import com.educloud.payment.entity.PaymentCallbackLogEntity;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.PaymentTransactionEntity;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import com.educloud.payment.feign.OrderClient;
import com.educloud.payment.feign.dto.OrderPayableSnapshotResponse;
import com.educloud.payment.mapper.PaymentCallbackLogMapper;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.PaymentTransactionMapper;
import com.educloud.payment.messaging.OutboxEventWriter;
import com.educloud.payment.messaging.events.PaymentSucceededEvent;
import com.educloud.payment.service.PaymentCallbackService;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.CallbackVerifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class PaymentCallbackServiceImpl implements PaymentCallbackService {

    /**
     * 回调审计日志独立事务（REQUIRES_NEW）：业务校验失败导致主事务回滚时，
     * 签名失败/金额篡改等安全事件记录不丢失（修复：审计随主事务回滚的问题）。
     */
    private final TransactionTemplate requiresNewTemplate;

    /** 带属主令牌的分布式锁释放脚本：仅当锁值匹配时才删除，避免处理超时后误删他人锁。 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentCallbackLogMapper callbackLogMapper;
    private final PaymentChannelFactory channelFactory;
    private final OutboxEventWriter outboxEventWriter;
    private final StringRedisTemplate redisTemplate;
    private final OrderClient orderClient;
    private final PaymentProperties properties;

    public PaymentCallbackServiceImpl(
            PaymentOrderMapper paymentOrderMapper,
            PaymentTransactionMapper paymentTransactionMapper,
            PaymentCallbackLogMapper callbackLogMapper,
            PaymentChannelFactory channelFactory,
            OutboxEventWriter outboxEventWriter,
            StringRedisTemplate redisTemplate,
            OrderClient orderClient,
            PaymentProperties properties,
            PlatformTransactionManager transactionManager) {
        this.paymentOrderMapper = Objects.requireNonNull(paymentOrderMapper, "paymentOrderMapper");
        this.paymentTransactionMapper = Objects.requireNonNull(paymentTransactionMapper, "paymentTransactionMapper");
        this.callbackLogMapper = Objects.requireNonNull(callbackLogMapper, "callbackLogMapper");
        this.channelFactory = Objects.requireNonNull(channelFactory, "channelFactory");
        this.outboxEventWriter = Objects.requireNonNull(outboxEventWriter, "outboxEventWriter");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.orderClient = Objects.requireNonNull(orderClient, "orderClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.requiresNewTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public String handleCallback(PaymentChannel channel, Map<String, String> headers, Map<String, String> params, String rawBody) {
        Objects.requireNonNull(channel, "channel");

        String payloadForHash = rawBody != null && !rawBody.isBlank() ? rawBody : (params != null ? params.toString() : "");
        String requestHash = calculateHash(payloadForHash);

        PaymentChannelPlugin plugin = channelFactory.getPlugin(channel);
        CallbackVerifyResult verifyResult = plugin.verifyAndParseCallback(headers, params, rawBody);

        String notifyId = verifyResult.getNotifyId() != null ? verifyResult.getNotifyId() : "NOTIFY_" + System.currentTimeMillis();
        String lockKey = "educloud:payment:lock:callback:" + channel.name() + ":" + (verifyResult.getPaymentOrderId() != null ? verifyResult.getPaymentOrderId() : notifyId);
        String lockValue = UUID.randomUUID().toString();

        // 防并发重放：Redis 排他锁（带属主令牌，解锁时校验持有者，防止超时后误删他人锁）
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));
        if (Boolean.FALSE.equals(locked)) {
            log.warn("Concurrent callback detected for lockKey={}, ignoring duplicate execution", lockKey);
            return verifyResult.getResponseMessage() != null ? verifyResult.getResponseMessage() : "success";
        }

        try {
            // 审计日志独立事务落库：即使后续校验失败导致主事务回滚，安全事件记录也不丢失
            PaymentCallbackLogEntity callbackLog = persistCallbackLog(channel, notifyId, requestHash, payloadForHash,
                    verifyResult.isValid() ? "SUCCESS" : "FAIL");
            if (callbackLog == null) {
                // 渠道重发同一 notify_id：uk_channel_notify 冲突，首次处理结果已落库，直接幂等 ACK
                return verifyResult.getResponseMessage() != null ? verifyResult.getResponseMessage() : "success";
            }

            if (!verifyResult.isValid()) {
                markLogFailed(callbackLog, verifyResult.getErrorMessage());
                log.warn("Payment callback verification failed for channel={}: {}", channel, verifyResult.getErrorMessage());
                throw new PaymentBizException(PaymentErrorCode.SIGN_VERIFY_FAILED, "签名校验失败: " + verifyResult.getErrorMessage());
            }

            Long paymentOrderId = verifyResult.getPaymentOrderId();
            if (paymentOrderId == null) {
                markLogFailed(callbackLog, "Missing paymentOrderId");
                throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "回调缺少支付单ID");
            }

            PaymentOrderEntity paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
            if (paymentOrder == null || paymentOrder.getDeleted() == 1) {
                markLogFailed(callbackLog, "PaymentOrder not found: " + paymentOrderId);
                throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在: " + paymentOrderId);
            }

            // 幂等：支付单已是终态 SUCCESS，直接 ACK，不再重复入账
            if (paymentOrder.getStatus() == PaymentStatus.SUCCESS) {
                markLogProcessed(callbackLog, "IDEMPOTENT_IGNORED", null);
                log.info("Payment order {} already SUCCESS, ignoring duplicate callback", paymentOrderId);
                return verifyResult.getResponseMessage() != null ? verifyResult.getResponseMessage() : "success";
            }

            // 金额防篡改校验
            if (verifyResult.getAmountCents() != null && !paymentOrder.getAmountCents().equals(verifyResult.getAmountCents())) {
                String errorMsg = String.format("Amount mismatch: expected=%d, actual=%d",
                        paymentOrder.getAmountCents(), verifyResult.getAmountCents());
                markLogFailed(callbackLog, errorMsg);
                log.error("CRITICAL SECURITY ALERT: {}", errorMsg);
                throw new PaymentBizException(PaymentErrorCode.AMOUNT_MISMATCH, errorMsg);
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime paidAt = verifyResult.getPaidAt() != null ? verifyResult.getPaidAt() : now;
            String channelTradeNo = verifyResult.getChannelTradeNo() != null ? verifyResult.getChannelTradeNo() : "TR_" + paymentOrderId;

            if (verifyResult.getStatus() == PaymentStatus.SUCCESS) {
                // 迟付防护：入账前校验业务订单仍可支付（PENDING_PAYMENT 且未过期）。
                // 订单已关单/取消/过期时拒绝入账并告警，避免“渠道已扣款但订单不可履约”的资损，
                // 该状态由日终对账发现并经人工退款/平账处理。
                if (!isBusinessOrderPayable(paymentOrder.getOrderId())) {
                    markLogFailed(callbackLog, "Business order no longer payable: " + paymentOrder.getOrderId());
                    paymentOrderMapper.updateStatusToTerminalCas(paymentOrderId, PaymentStatus.FAILED);
                    log.error("PAYMENT-ORDER-MISMATCH: callback for paymentOrderId={} rejected because business order {} is not payable; manual refund/reconciliation required",
                            paymentOrderId, paymentOrder.getOrderId());
                    return verifyResult.getResponseMessage() != null ? verifyResult.getResponseMessage() : "success";
                }

                int updated = paymentOrderMapper.updateStatusToSuccessCas(
                        paymentOrderId, PaymentStatus.SUCCESS, paidAt, channelTradeNo, now);

                if (updated == 1) {
                    PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                            .paymentOrderId(paymentOrder.getId())
                            .orderId(paymentOrder.getOrderId())
                            .userId(paymentOrder.getUserId())
                            .amountCents(paymentOrder.getAmountCents())
                            .channelCode(paymentOrder.getChannelCode())
                            .channelTradeNo(channelTradeNo)
                            .paidAt(paidAt)
                            .build();

                    outboxEventWriter.writeEvent("PAYMENT", paymentOrder.getId(), "PaymentSucceededEvent", event);

                    PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                            .id(IdWorker.getId())
                            .paymentOrderId(paymentOrder.getId())
                            .transactionNo("TX_CB_" + paymentOrderId + "_" + System.currentTimeMillis())
                            .channelCode(channel)
                            .actionType("CALLBACK_SUCCESS")
                            .amountCents(paymentOrder.getAmountCents())
                            .feeCents(0L)
                            .rawRequest(payloadForHash)
                            .rawResponse(verifyResult.getResponseMessage())
                            .status("SUCCESS")
                            .createdAt(now)
                            .build();
                    paymentTransactionMapper.insert(transaction);

                    markLogProcessed(callbackLog, "PROCESSED", null);
                    log.info("Payment order {} successfully transitioned to SUCCESS via callback", paymentOrderId);
                } else {
                    // CAS 失败：并发下已被其他回调处理为终态，幂等忽略
                    markLogProcessed(callbackLog, "IDEMPOTENT_IGNORED", null);
                    log.info("Payment order {} callback ignored (already in terminal state)", paymentOrderId);
                }
            } else if (verifyResult.getStatus() == PaymentStatus.FAILED) {
                paymentOrderMapper.updateStatusToTerminalCas(paymentOrderId, PaymentStatus.FAILED);
                markLogProcessed(callbackLog, "PROCESSED_FAILED", null);
            }

            return verifyResult.getResponseMessage() != null ? verifyResult.getResponseMessage() : "success";
        } finally {
            // 仅当锁值仍为本请求持有才释放（Lua 原子比对删除）
            try {
                redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
            } catch (Exception e) {
                log.warn("Failed to release callback lock lockKey={}", lockKey, e);
            }
        }
    }

    /**
     * 回调审计日志以 REQUIRES_NEW 独立事务写入，与主业务事务解耦：
     * 主事务因校验失败回滚时，审计记录仍然保留（安全事件可追溯）。
     *
     * @return 落库后的日志实体；重复 notifyId 冲突时返回 null（幂等 ACK 不再处理）
     */
    private PaymentCallbackLogEntity persistCallbackLog(
            PaymentChannel channel, String notifyId, String requestHash, String payload, String verifyResult) {
        try {
            return requiresNewTemplate.execute(status -> {
                PaymentCallbackLogEntity callbackLog = PaymentCallbackLogEntity.builder()
                        .id(IdWorker.getId())
                        .channelCode(channel)
                        .notifyId(notifyId)
                        .requestHash(requestHash)
                        .rawPayload(payload)
                        .verifyResult(verifyResult)
                        .processedStatus("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                callbackLogMapper.insert(callbackLog);
                return callbackLog;
            });
        } catch (DuplicateKeyException duplicate) {
            // 可靠性修复（M08 审查）：渠道重发同一 notify_id 时 uk_channel_notify 冲突。
            // 若直接报 500，渠道会持续重试（支付宝最长 24h）；首次处理结果已落库，此处幂等 ACK。
            log.info("Duplicate callback notification ACKed without reprocessing: channel={}, notifyId={}", channel, notifyId);
            return null;
        }
    }

    /** 以独立事务更新回调日志处理状态（主事务回滚时状态更新不丢失）。 */
    private void markLogProcessed(PaymentCallbackLogEntity callbackLog, String processedStatus, String errorMsg) {
        if (callbackLog == null || callbackLog.getId() == null) {
            return;
        }
        try {
            requiresNewTemplate.executeWithoutResult(status -> {
                PaymentCallbackLogEntity update = new PaymentCallbackLogEntity();
                update.setId(callbackLog.getId());
                update.setProcessedStatus(processedStatus);
                update.setErrorMsg(errorMsg);
                callbackLogMapper.updateById(update);
            });
        } catch (Exception e) {
            log.error("Failed to update callback log {} to status {}", callbackLog.getId(), processedStatus, e);
        }
    }

    private void markLogFailed(PaymentCallbackLogEntity callbackLog, String errorMsg) {
        markLogProcessed(callbackLog, "FAILED", errorMsg);
    }

    /**
     * 业务订单可付性校验：PENDING_PAYMENT 且未过期。
     * 查询失败按不可付处理（fail-closed）：宁可拒绝本次入账让渠道重试，
     * 也不产生“渠道已扣款但订单无法履约”的资损订单。
     */
    private boolean isBusinessOrderPayable(Long orderId) {
        if (orderId == null) {
            return false;
        }
        String internalSecret = properties.internal() != null ? properties.internal().secretToken() : null;
        try {
            ApiResponse<OrderPayableSnapshotResponse> resp = orderClient.getPayableSnapshot(orderId, internalSecret);
            if (resp == null || resp.data() == null) {
                return false;
            }
            OrderPayableSnapshotResponse snapshot = resp.data();
            if (!"PENDING_PAYMENT".equalsIgnoreCase(snapshot.getStatus())) {
                return false;
            }
            return snapshot.getExpiresAt() == null || !snapshot.getExpiresAt().isBefore(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to verify business order payability for orderId={}, treating as not payable", orderId, e);
            return false;
        }
    }

    private String calculateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "HASH_ERROR_" + System.currentTimeMillis();
        }
    }
}
