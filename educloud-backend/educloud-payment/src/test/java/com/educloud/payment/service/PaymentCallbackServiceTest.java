package com.educloud.payment.service;

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
import com.educloud.payment.service.impl.PaymentCallbackServiceImpl;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.plugins.MockPaymentPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackServiceTest {

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private PaymentTransactionMapper paymentTransactionMapper;

    @Mock
    private PaymentCallbackLogMapper callbackLogMapper;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PaymentCallbackServiceImpl callbackService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        PaymentChannelFactory channelFactory = new PaymentChannelFactory(List.of(new MockPaymentPlugin()));
        callbackService = new PaymentCallbackServiceImpl(
                paymentOrderMapper,
                paymentTransactionMapper,
                callbackLogMapper,
                channelFactory,
                outboxEventWriter,
                redisTemplate
        );
    }

    @Test
    void handleCallback_mockSuccess() {
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .userId(2091648316809035778L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.PAYING)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);
        when(paymentOrderMapper.updateStatusToSuccessCas(eq(2091998812345678901L), eq(PaymentStatus.SUCCESS), any(), any(), any()))
                .thenReturn(1);

        String json = "{\"paymentOrderId\":2091998812345678901,\"amountCents\":19900,\"notifyId\":\"NOTIFY_001\",\"channelTradeNo\":\"TR_001\"}";
        String response = callbackService.handleCallback(PaymentChannel.MOCK, Map.of(), Map.of(), json);

        assertNotNull(response);
        verify(callbackLogMapper).insert(any(PaymentCallbackLogEntity.class));
        verify(outboxEventWriter).writeEvent(eq("PAYMENT"), eq(2091998812345678901L), eq("PaymentSucceededEvent"), any());
        verify(paymentTransactionMapper).insert(any(PaymentTransactionEntity.class));
    }

    @Test
    void handleCallback_amountMismatch_throwsException() {
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .userId(2091648316809035778L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.PAYING)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);

        // 篡改金额为 1 分钱
        String json = "{\"paymentOrderId\":2091998812345678901,\"amountCents\":1,\"notifyId\":\"NOTIFY_001\"}";

        PaymentBizException exception = assertThrows(PaymentBizException.class,
                () -> callbackService.handleCallback(PaymentChannel.MOCK, Map.of(), Map.of(), json));

        assertEquals(PaymentErrorCode.AMOUNT_MISMATCH, exception.errorCode());
        verify(outboxEventWriter, never()).writeEvent(any(), any(), any(), any());
    }

    @Test
    void handleCallback_idempotentSecondCallback_doesNotDuplicateOutboxEvent() {
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .userId(2091648316809035778L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.SUCCESS)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);
        // CAS 失败（因为已经是 SUCCESS）
        when(paymentOrderMapper.updateStatusToSuccessCas(eq(2091998812345678901L), eq(PaymentStatus.SUCCESS), any(), any(), any()))
                .thenReturn(0);

        String json = "{\"paymentOrderId\":2091998812345678901,\"amountCents\":19900,\"notifyId\":\"NOTIFY_001\",\"channelTradeNo\":\"TR_001\"}";
        String response = callbackService.handleCallback(PaymentChannel.MOCK, Map.of(), Map.of(), json);

        assertNotNull(response);
        verify(outboxEventWriter, never()).writeEvent(any(), any(), any(), any());
    }
}
