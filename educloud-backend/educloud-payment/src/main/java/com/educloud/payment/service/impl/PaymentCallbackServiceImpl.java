package com.educloud.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.payment.entity.PaymentCallbackLogEntity;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.PaymentTransactionEntity;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import com.educloud.payment.mapper.PaymentCallbackLogMapper;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.PaymentTransactionMapper;
import com.educloud.payment.messaging.OutboxEventWriter;
import com.educloud.payment.messaging.events.PaymentSucceededEvent;
import com.educloud.payment.service.PaymentCallbackService;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.CallbackVerifyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackServiceImpl implements PaymentCallbackService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentCallbackLogMapper callbackLogMapper;
    private final PaymentChannelFactory channelFactory;
    private final OutboxEventWriter outboxEventWriter;
    private final StringRedisTemplate redisTemplate;

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

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(10));
        if (Boolean.FALSE.equals(locked)) {
            log.warn("Concurrent callback detected for lockKey={}, ignoring duplicate execution", lockKey);
            return verifyResult.getResponseMessage() != null ? verifyResult.getResponseMessage() : "success";
        }

        PaymentCallbackLogEntity callbackLog = PaymentCallbackLogEntity.builder()
                .id(IdWorker.getId())
                .channelCode(channel)
                .notifyId(notifyId)
                .requestHash(requestHash)
                .rawPayload(payloadForHash)
                .verifyResult(verifyResult.isValid() ? "SUCCESS" : "FAIL")
                .processedStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        callbackLogMapper.insert(callbackLog);

        try {
            if (!verifyResult.isValid()) {
                callbackLog.setErrorMsg(verifyResult.getErrorMessage());
                callbackLog.setProcessedStatus("FAILED");
                callbackLogMapper.updateById(callbackLog);
                log.warn("Payment callback verification failed for channel={}: {}", channel, verifyResult.getErrorMessage());
                throw new PaymentBizException(PaymentErrorCode.SIGN_VERIFY_FAILED, "签名校验失败: " + verifyResult.getErrorMessage());
            }

            Long paymentOrderId = verifyResult.getPaymentOrderId();
            if (paymentOrderId == null) {
                callbackLog.setErrorMsg("Missing paymentOrderId");
                callbackLog.setProcessedStatus("FAILED");
                callbackLogMapper.updateById(callbackLog);
                throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "回调缺少支付单ID");
            }

            PaymentOrderEntity paymentOrder = paymentOrderMapper.selectById(paymentOrderId);
            if (paymentOrder == null || paymentOrder.getDeleted() == 1) {
                callbackLog.setErrorMsg("PaymentOrder not found: " + paymentOrderId);
                callbackLog.setProcessedStatus("FAILED");
                callbackLogMapper.updateById(callbackLog);
                throw new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在: " + paymentOrderId);
            }

            // 金额防篡改校验
            if (verifyResult.getAmountCents() != null && !paymentOrder.getAmountCents().equals(verifyResult.getAmountCents())) {
                String errorMsg = String.format("Amount mismatch: expected=%d, actual=%d",
                        paymentOrder.getAmountCents(), verifyResult.getAmountCents());
                callbackLog.setErrorMsg(errorMsg);
                callbackLog.setProcessedStatus("FAILED");
                callbackLogMapper.updateById(callbackLog);
                log.error("CRITICAL SECURITY ALERT: {}", errorMsg);
                throw new PaymentBizException(PaymentErrorCode.AMOUNT_MISMATCH, errorMsg);
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime paidAt = verifyResult.getPaidAt() != null ? verifyResult.getPaidAt() : now;
            String channelTradeNo = verifyResult.getChannelTradeNo() != null ? verifyResult.getChannelTradeNo() : "TR_" + paymentOrderId;

            if (verifyResult.getStatus() == PaymentStatus.SUCCESS) {
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

                    callbackLog.setProcessedStatus("PROCESSED");
                    callbackLogMapper.updateById(callbackLog);
                    log.info("Payment order {} successfully transitioned to SUCCESS via callback", paymentOrderId);
                } else {
                    // 已是 SUCCESS 则是幂等通知；若是 CLOSED/FAILED 则记录
                    callbackLog.setProcessedStatus("IDEMPOTENT_IGNORED");
                    callbackLogMapper.updateById(callbackLog);
                    log.info("Payment order {} callback ignored (already in terminal state)", paymentOrderId);
                }
            } else if (verifyResult.getStatus() == PaymentStatus.FAILED) {
                paymentOrderMapper.updateStatusToTerminalCas(paymentOrderId, PaymentStatus.FAILED);
                callbackLog.setProcessedStatus("PROCESSED_FAILED");
                callbackLogMapper.updateById(callbackLog);
            }

            return verifyResult.getResponseMessage() != null ? verifyResult.getResponseMessage() : "success";
        } finally {
            redisTemplate.delete(lockKey);
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
