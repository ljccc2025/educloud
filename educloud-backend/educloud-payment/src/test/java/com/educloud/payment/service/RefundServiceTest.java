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
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.RefundContext;
import com.educloud.payment.spi.model.UnifiedRefundResult;
import com.educloud.payment.spi.plugins.MockPaymentPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    /** 用自定义插件构造服务（P2-7 消歧/收敛路径测试用）。 */
    private RefundServiceImpl buildService(PaymentChannelPlugin plugin) {
        PaymentChannelFactory channelFactory = new PaymentChannelFactory(List.of(plugin));
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        return new RefundServiceImpl(
                refundMapper,
                paymentOrderMapper,
                transactionMapper,
                channelFactory,
                outboxEventWriter,
                transactionTemplate
        );
    }

    /** 可编程退款桩：模拟渠道退款失败/查单结果，覆盖 P2-7 消歧与定时收敛路径。 */
    static class StubRefundPlugin extends MockPaymentPlugin {
        private final UnifiedRefundResult initiateResult;
        private final UnifiedRefundResult queryResult;

        StubRefundPlugin(UnifiedRefundResult initiateResult, UnifiedRefundResult queryResult) {
            super(new PaymentProperties("local", null, null, null, null));
            this.initiateResult = initiateResult;
            this.queryResult = queryResult;
        }

        @Override
        public UnifiedRefundResult initiateRefund(RefundContext context) {
            return initiateResult;
        }

        @Override
        public UnifiedRefundResult queryRefund(RefundContext context) {
            return queryResult;
        }
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

    // ---------- P2-7（08-26 审查）：渠道失败先查单消歧，不再钉死 FAILED ----------

    @Test
    void auditRefund_channelFailed_queryConfirmsRefunded_convergesToSuccess() {
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
                .userId(2091648316809035778L)
                .build();

        when(refundMapper.selectById(2091999912345678902L)).thenReturn(refund);
        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of());
        when(refundMapper.updateStatusCas(eq(2091999912345678902L), eq(RefundStatus.PROCESSING), eq(RefundStatus.SUCCESS), any(), any()))
                .thenReturn(1);

        UnifiedRefundResult channelFailure = UnifiedRefundResult.builder()
                .success(false)
                .errorCode("TIMEOUT")
                .errorMessage("channel refund timeout")
                .build();
        UnifiedRefundResult queryConfirmed = UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.SUCCESS)
                .channelRefundNo("MOCK_REF_2091999912345678902")
                .refundedAt(LocalDateTime.now())
                .build();
        RefundServiceImpl service = buildService(new StubRefundPlugin(channelFailure, queryConfirmed));

        RefundDetailResponse response = service.auditRefund(1L, 2091999912345678902L,
                RefundAuditRequest.builder().approve(true).remark("同意退款").build());

        // 渠道失败但查单确认已退：成功收敛，事件只发一次。
        assertEquals(RefundStatus.SUCCESS, response.getStatus());
        verify(outboxEventWriter).writeEvent(eq("REFUND"), eq(2091999912345678902L), eq("PaymentRefundedEvent"), any());
        verify(transactionMapper).insert(any(PaymentTransactionEntity.class));
    }

    @Test
    void auditRefund_channelFailed_queryConfirmsNotRefunded_marksFailed() {
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
        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of());
        when(refundMapper.updateStatusOnlyCas(eq(2091999912345678902L), eq(RefundStatus.PROCESSING), eq(RefundStatus.FAILED)))
                .thenReturn(1);

        UnifiedRefundResult channelFailure = UnifiedRefundResult.builder()
                .success(false)
                .errorCode("CHANNEL_ERROR")
                .errorMessage("channel rejected refund")
                .build();
        UnifiedRefundResult queryNotRefunded = UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.FAILED)
                .build();
        RefundServiceImpl service = buildService(new StubRefundPlugin(channelFailure, queryNotRefunded));

        RefundDetailResponse response = service.auditRefund(1L, 2091999912345678902L,
                RefundAuditRequest.builder().approve(true).remark("同意退款").build());

        // 渠道确认未退：置 FAILED（允许重新审核），不发成功事件。
        assertEquals(RefundStatus.FAILED, response.getStatus());
        verify(refundMapper).updateStatusOnlyCas(eq(2091999912345678902L), eq(RefundStatus.PROCESSING), eq(RefundStatus.FAILED));
        verify(outboxEventWriter, never()).writeEvent(anyString(), any(), anyString(), any());
    }

    @Test
    void auditRefund_channelFailed_queryAmbiguous_staysProcessing() {
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
        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of());

        UnifiedRefundResult channelFailure = UnifiedRefundResult.builder()
                .success(false)
                .errorCode("TIMEOUT")
                .errorMessage("channel refund timeout")
                .build();
        UnifiedRefundResult queryAmbiguous = UnifiedRefundResult.builder()
                .success(false)
                .errorCode("QUERY_TIMEOUT")
                .errorMessage("refund query timeout")
                .build();
        RefundServiceImpl service = buildService(new StubRefundPlugin(channelFailure, queryAmbiguous));

        RefundDetailResponse response = service.auditRefund(1L, 2091999912345678902L,
                RefundAuditRequest.builder().approve(true).remark("同意退款").build());

        // 查单也失败（二义）：保持 PROCESSING，由定时任务收敛；不发事件、不落终态。
        assertEquals(RefundStatus.PROCESSING, response.getStatus());
        verify(outboxEventWriter, never()).writeEvent(anyString(), any(), anyString(), any());
        verify(refundMapper, never()).updateStatusOnlyCas(any(), any(), any());
        verify(refundMapper, never()).updateStatusCas(any(), any(), any(), any(), any());
    }

    // ---------- P2-7：FAILED 单可重新审核 ----------

    @Test
    void auditRefund_failedRefund_canReauditToSuccess() {
        PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .id(2091999912345678902L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(RefundStatus.FAILED)
                .build();
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .channelTradeNo("MOCK_TR_001")
                .userId(2091648316809035778L)
                .build();

        when(refundMapper.selectById(2091999912345678902L)).thenReturn(refund);
        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of());
        when(refundMapper.updateStatusCas(eq(2091999912345678902L), eq(RefundStatus.PROCESSING), eq(RefundStatus.SUCCESS), any(), any()))
                .thenReturn(1);

        // 重审时渠道退款成功：FAILED → PROCESSING → SUCCESS，事件只发一次。
        RefundServiceImpl service = buildService(new MockPaymentPlugin(new PaymentProperties("local", null, null, null, null)));

        RefundDetailResponse response = service.auditRefund(1L, 2091999912345678902L,
                RefundAuditRequest.builder().approve(true).remark("重新审核").build());

        assertEquals(RefundStatus.SUCCESS, response.getStatus());
        verify(outboxEventWriter).writeEvent(eq("REFUND"), eq(2091999912345678902L), eq("PaymentRefundedEvent"), any());
        verify(transactionMapper).insert(any(PaymentTransactionEntity.class));
    }

    @Test
    void auditRefund_failedRefund_canReauditReject() {
        PaymentRefundEntity refund = PaymentRefundEntity.builder()
                .id(2091999912345678902L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .status(RefundStatus.FAILED)
                .build();

        when(refundMapper.selectById(2091999912345678902L)).thenReturn(refund);

        RefundDetailResponse response = refundService.auditRefund(1L, 2091999912345678902L,
                RefundAuditRequest.builder().approve(false).remark("渠道侧确认未退，驳回重试").build());

        assertEquals(RefundStatus.REJECTED, response.getStatus());
    }

    // ---------- P2-7：定时查单收敛任务 ----------

    @Test
    void reconcileStuckRefunds_queryConfirmsRefunded_convergesToSuccess() {
        PaymentRefundEntity stuck = PaymentRefundEntity.builder()
                .id(2091999912345678903L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(RefundStatus.PROCESSING)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .channelTradeNo("MOCK_TR_001")
                .userId(2091648316809035778L)
                .build();

        when(refundMapper.selectList(any())).thenReturn(List.of(stuck));
        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);
        when(refundMapper.updateStatusCas(eq(2091999912345678903L), eq(RefundStatus.PROCESSING), eq(RefundStatus.SUCCESS), any(), any()))
                .thenReturn(1);

        UnifiedRefundResult queryConfirmed = UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.SUCCESS)
                .channelRefundNo("MOCK_REF_2091999912345678903")
                .refundedAt(LocalDateTime.now())
                .build();
        RefundServiceImpl service = buildService(new StubRefundPlugin(
                UnifiedRefundResult.builder().success(true).status(RefundStatus.SUCCESS).build(), queryConfirmed));

        service.reconcileStuckRefunds();

        // 超时 PROCESSING 单查单确认已退：CAS 收敛 SUCCESS，事件只发一次。
        verify(refundMapper).updateStatusCas(eq(2091999912345678903L), eq(RefundStatus.PROCESSING), eq(RefundStatus.SUCCESS), any(), any());
        verify(outboxEventWriter).writeEvent(eq("REFUND"), eq(2091999912345678903L), eq("PaymentRefundedEvent"), any());
        verify(transactionMapper).insert(any(PaymentTransactionEntity.class));
    }

    @Test
    void reconcileStuckRefunds_queryConfirmsNotRefunded_marksFailed() {
        PaymentRefundEntity stuck = PaymentRefundEntity.builder()
                .id(2091999912345678903L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(RefundStatus.PROCESSING)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .channelTradeNo("MOCK_TR_001")
                .build();

        when(refundMapper.selectList(any())).thenReturn(List.of(stuck));
        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);
        when(refundMapper.updateStatusOnlyCas(eq(2091999912345678903L), eq(RefundStatus.PROCESSING), eq(RefundStatus.FAILED)))
                .thenReturn(1);

        UnifiedRefundResult queryNotRefunded = UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.FAILED)
                .build();
        RefundServiceImpl service = buildService(new StubRefundPlugin(
                UnifiedRefundResult.builder().success(true).status(RefundStatus.SUCCESS).build(), queryNotRefunded));

        service.reconcileStuckRefunds();

        verify(refundMapper).updateStatusOnlyCas(eq(2091999912345678903L), eq(RefundStatus.PROCESSING), eq(RefundStatus.FAILED));
        verify(outboxEventWriter, never()).writeEvent(anyString(), any(), anyString(), any());
    }

    @Test
    void reconcileStuckRefunds_queryAmbiguous_keepsProcessing() {
        PaymentRefundEntity stuck = PaymentRefundEntity.builder()
                .id(2091999912345678903L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(RefundStatus.PROCESSING)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .channelTradeNo("MOCK_TR_001")
                .build();

        when(refundMapper.selectList(any())).thenReturn(List.of(stuck));
        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);

        UnifiedRefundResult queryAmbiguous = UnifiedRefundResult.builder()
                .success(false)
                .errorCode("QUERY_TIMEOUT")
                .errorMessage("refund query timeout")
                .build();
        RefundServiceImpl service = buildService(new StubRefundPlugin(
                UnifiedRefundResult.builder().success(true).status(RefundStatus.SUCCESS).build(), queryAmbiguous));

        service.reconcileStuckRefunds();

        // 仍二义：保持 PROCESSING，不发事件、不落终态，下轮扫描重试。
        verify(outboxEventWriter, never()).writeEvent(anyString(), any(), anyString(), any());
        verify(refundMapper, never()).updateStatusOnlyCas(any(), any(), any());
        verify(refundMapper, never()).updateStatusCas(any(), any(), any(), any(), any());
    }
}
