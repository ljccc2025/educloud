package com.educloud.payment.service;

import com.educloud.payment.config.PaymentProperties;
import com.educloud.payment.dto.request.RefundApplyRequest;
import com.educloud.payment.dto.request.RefundAuditRequest;
import com.educloud.payment.dto.response.RefundDetailResponse;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.PaymentRefundEntity;
import com.educloud.payment.entity.PaymentTransactionEntity;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.RefundStatus;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.PaymentRefundMapper;
import com.educloud.payment.mapper.PaymentTransactionMapper;
import com.educloud.payment.messaging.OutboxEventWriter;
import com.educloud.payment.service.impl.RefundServiceImpl;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.plugins.MockPaymentPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private PaymentRefundMapper refundMapper;

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private PaymentTransactionMapper transactionMapper;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        PaymentChannelFactory channelFactory = new PaymentChannelFactory(List.of(
                new MockPaymentPlugin(new PaymentProperties("local", null, null, null, null))));
        // 事务修复（M08 审查）：auditRefund 拆为三段短事务，测试用同步执行的
        // TransactionTemplate（mock 事务管理器：getTransaction/commit 均空实现）。
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        refundService = new RefundServiceImpl(
                refundMapper,
                paymentOrderMapper,
                transactionMapper,
                channelFactory,
                outboxEventWriter,
                transactionTemplate
        );
    }

    @Test
    void applyRefund_success() {
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .currency("CNY")
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.SUCCESS)
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of());

        RefundApplyRequest request = RefundApplyRequest.builder()
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .reason("测试退款")
                .build();

        RefundDetailResponse response = refundService.applyRefund(2091648316809035778L, request);

        assertNotNull(response);
        assertEquals(RefundStatus.APPLIED, response.getStatus());
        assertEquals(19900L, response.getRefundAmountCents());
        verify(refundMapper).insert(any(PaymentRefundEntity.class));
    }

    @Test
    void applyRefund_amountExceeded_throwsException() {
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .currency("CNY")
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.SUCCESS)
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of(
                PaymentRefundEntity.builder().refundAmountCents(10000L).status(RefundStatus.SUCCESS).build()
        ));

        RefundApplyRequest request = RefundApplyRequest.builder()
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(15000L) // 10000 + 15000 > 19900
                .reason("测试超额退款")
                .build();

        PaymentBizException exception = assertThrows(PaymentBizException.class,
                () -> refundService.applyRefund(2091648316809035778L, request));

        assertEquals(PaymentErrorCode.REFUND_AMOUNT_EXCEEDED, exception.errorCode());
    }

    @Test
    void auditRefund_approve_success() {
        PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .id(2091999912345678902L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(RefundStatus.APPLIED)
                .build();

        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .channelTradeNo("MOCK_TR_001")
                .build();

        when(refundMapper.selectById(2091999912345678902L)).thenReturn(refund);
        // 审核通过前加锁复验：selectOne（FOR UPDATE）返回支付单，其余退款单为空。
        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of());
        when(refundMapper.updateStatusCas(eq(2091999912345678902L), eq(RefundStatus.PROCESSING), eq(RefundStatus.SUCCESS), any(), any()))
                .thenReturn(1);

        RefundAuditRequest request = RefundAuditRequest.builder()
                .approve(true)
                .remark("同意退款")
                .build();

        RefundDetailResponse response = refundService.auditRefund(1L, 2091999912345678902L, request);

        assertNotNull(response);
        assertEquals(RefundStatus.SUCCESS, response.getStatus());
        verify(outboxEventWriter).writeEvent(eq("REFUND"), eq(2091999912345678902L), eq("PaymentRefundedEvent"), any());
        verify(transactionMapper).insert(any(PaymentTransactionEntity.class));
    }

    @Test
    void auditRefund_reject_success() {
        PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .id(2091999912345678902L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .status(RefundStatus.APPLIED)
                .build();

        when(refundMapper.selectById(2091999912345678902L)).thenReturn(refund);

        RefundAuditRequest request = RefundAuditRequest.builder()
                .approve(false)
                .remark("超过退款有效期限")
                .build();

        RefundDetailResponse response = refundService.auditRefund(1L, 2091999912345678902L, request);

        assertNotNull(response);
        assertEquals(RefundStatus.REJECTED, response.getStatus());
        assertEquals("超过退款有效期限", response.getAuditRemark());
    }
}
