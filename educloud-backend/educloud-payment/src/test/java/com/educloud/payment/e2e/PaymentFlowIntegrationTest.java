package com.educloud.payment.e2e;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.api.ApiResponse;
import com.educloud.payment.config.PaymentProperties;
import com.educloud.payment.dto.request.CashierPayRequest;
import com.educloud.payment.dto.request.ReconcileDiffResolveRequest;
import com.educloud.payment.dto.request.RefundApplyRequest;
import com.educloud.payment.dto.request.RefundAuditRequest;
import com.educloud.payment.dto.response.CashierPayResponse;
import com.educloud.payment.dto.response.PaymentDetailResponse;
import com.educloud.payment.dto.response.ReconciliationBatchResponse;
import com.educloud.payment.dto.response.ReconciliationDiffResponse;
import com.educloud.payment.dto.response.RefundDetailResponse;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.PaymentRefundEntity;
import com.educloud.payment.entity.PaymentTransactionEntity;
import com.educloud.payment.entity.ReconciliationBatchEntity;
import com.educloud.payment.entity.ReconciliationDiffEntity;
import com.educloud.payment.enums.DiffType;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.ReconciliationBatchStatus;
import com.educloud.payment.enums.RefundStatus;
import com.educloud.payment.enums.ResolveAction;
import com.educloud.payment.enums.ResolveStatus;
import com.educloud.payment.enums.TradeType;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import com.educloud.payment.feign.OrderClient;
import com.educloud.payment.feign.dto.OrderPayableSnapshotResponse;
import com.educloud.payment.mapper.PaymentCallbackLogMapper;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.PaymentOutboxEventMapper;
import com.educloud.payment.mapper.PaymentRefundMapper;
import com.educloud.payment.mapper.PaymentTransactionMapper;
import com.educloud.payment.mapper.ReconciliationBatchMapper;
import com.educloud.payment.mapper.ReconciliationDiffMapper;
import com.educloud.payment.messaging.OutboxEventWriter;
import com.educloud.payment.service.PaymentCallbackService;
import com.educloud.payment.service.PaymentService;
import com.educloud.payment.service.ReconciliationService;
import com.educloud.payment.service.RefundService;
import com.educloud.payment.service.impl.PaymentCallbackServiceImpl;
import com.educloud.payment.service.impl.PaymentServiceImpl;
import com.educloud.payment.service.impl.ReconciliationServiceImpl;
import com.educloud.payment.service.impl.RefundServiceImpl;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.model.ChannelBillItem;
import com.educloud.payment.spi.plugins.AlipayEasySdkPlugin;
import com.educloud.payment.spi.plugins.MockPaymentPlugin;
import com.educloud.payment.spi.plugins.WeChatPayV3Plugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFlowIntegrationTest {

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private PaymentTransactionMapper paymentTransactionMapper;

    @Mock
    private PaymentCallbackLogMapper callbackLogMapper;

    @Mock
    private PaymentRefundMapper refundMapper;

    @Mock
    private ReconciliationBatchMapper batchMapper;

    @Mock
    private ReconciliationDiffMapper diffMapper;

    @Mock
    private PaymentOutboxEventMapper outboxEventMapper;

    @Mock
    private OrderClient orderClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PaymentProperties properties;
    private PaymentChannelFactory channelFactory;
    private OutboxEventWriter outboxEventWriter;
    private PaymentService paymentService;
    private PaymentCallbackService callbackService;
    private RefundService refundService;
    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties(
                "local",
                new PaymentProperties.JwtProperties("file:/tmp/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                new PaymentProperties.InternalProperties("gateway", "educloud-payment", "secret"),
                new PaymentProperties.AlipayProperties("appId", "priv", "pub", "http://notify", "openapi-sandbox.dl.alipaydev.com"),
                new PaymentProperties.WechatProperties("appId", "mchId", "abcdef0123456789abcdef0123456789", "serial", "key.pem", "http://notify")
        );

        MockPaymentPlugin mockPlugin = new MockPaymentPlugin();
        AlipayEasySdkPlugin alipayPlugin = new AlipayEasySdkPlugin(properties);
        WeChatPayV3Plugin wechatPlugin = new WeChatPayV3Plugin(properties);

        channelFactory = new PaymentChannelFactory(List.of(mockPlugin, alipayPlugin, wechatPlugin));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        outboxEventWriter = new OutboxEventWriter(outboxEventMapper, objectMapper);

        paymentService = new PaymentServiceImpl(
                paymentOrderMapper,
                paymentTransactionMapper,
                channelFactory,
                outboxEventWriter,
                orderClient,
                properties
        );

        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        callbackService = new PaymentCallbackServiceImpl(
                paymentOrderMapper,
                paymentTransactionMapper,
                callbackLogMapper,
                channelFactory,
                outboxEventWriter,
                redisTemplate
        );

        refundService = new RefundServiceImpl(
                refundMapper,
                paymentOrderMapper,
                paymentTransactionMapper,
                channelFactory,
                outboxEventWriter
        );

        reconciliationService = new ReconciliationServiceImpl(
                batchMapper,
                diffMapper,
                paymentOrderMapper,
                channelFactory
        );
    }

    @Test
    @DisplayName("Stage 1 & 2: 收银台多渠道创建与下单流转")
    void testStage1And2_cashierCreation() {
        OrderPayableSnapshotResponse snapshot = OrderPayableSnapshotResponse.builder()
                .orderId(2091895618182258690L)
                .orderNo("ORD20260825001")
                .studentId(2091648316809035778L)
                .status("PENDING_PAYMENT")
                .payableAmount(new BigDecimal("199.00"))
                .currency("CNY")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        when(orderClient.getPayableSnapshot(eq(2091895618182258690L), any()))
                .thenReturn(new ApiResponse<>("SUCCESS", "OK", snapshot, "req-1", Instant.now()));
        when(paymentOrderMapper.selectOne(any())).thenReturn(null);

        // 1. MOCK 渠道
        CashierPayRequest mockReq = CashierPayRequest.builder()
                .orderId(2091895618182258690L)
                .channelCode(PaymentChannel.MOCK)
                .tradeType(TradeType.NATIVE)
                .build();
        CashierPayResponse mockResp = paymentService.createCashierPayment(2091648316809035778L, mockReq);
        assertNotNull(mockResp);
        assertEquals(PaymentStatus.PAYING, mockResp.getStatus());

        // 2. ALIPAY 渠道
        CashierPayRequest aliReq = CashierPayRequest.builder()
                .orderId(2091895618182258690L)
                .channelCode(PaymentChannel.ALIPAY)
                .tradeType(TradeType.PAGE)
                .build();
        CashierPayResponse aliResp = paymentService.createCashierPayment(2091648316809035778L, aliReq);
        assertNotNull(aliResp);
        assertTrue(aliResp.getPayUrl().contains("gateway.do"));

        // 3. WECHAT 渠道
        CashierPayRequest wxReq = CashierPayRequest.builder()
                .orderId(2091895618182258690L)
                .channelCode(PaymentChannel.WECHAT)
                .tradeType(TradeType.NATIVE)
                .build();
        CashierPayResponse wxResp = paymentService.createCashierPayment(2091648316809035778L, wxReq);
        assertNotNull(wxResp);
        assertTrue(wxResp.getQrCode().startsWith("weixin://"));
    }

    @Test
    @DisplayName("Stage 3, 4 & 5: 回调验签、金额防篡改与发件箱事件写入")
    void testStage3To5_callbackAndOutbox() {
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

        // 金额防篡改
        String tamperedJson = "{\"paymentOrderId\":2091998812345678901,\"amountCents\":9900,\"notifyId\":\"N_01\"}";
        assertThrows(PaymentBizException.class, () ->
                callbackService.handleCallback(PaymentChannel.MOCK, Map.of(), Map.of(), tamperedJson));

        // 正确回调
        when(paymentOrderMapper.updateStatusToSuccessCas(eq(2091998812345678901L), eq(PaymentStatus.SUCCESS), any(), any(), any()))
                .thenReturn(1);
        String validJson = "{\"paymentOrderId\":2091998812345678901,\"amountCents\":19900,\"notifyId\":\"N_01\",\"channelTradeNo\":\"TR_01\"}";
        String result = callbackService.handleCallback(PaymentChannel.MOCK, Map.of(), Map.of(), validJson);

        assertNotNull(result);
        verify(outboxEventMapper).insert(any(com.educloud.payment.entity.PaymentOutboxEventEntity.class));
        verify(paymentTransactionMapper).insert(any(PaymentTransactionEntity.class));
    }

    @Test
    @DisplayName("Stage 7: 逆向退款申请、财务审核与原路退款")
    void testStage7_refundFlow() {
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .userId(2091648316809035778L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.SUCCESS)
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);
        when(refundMapper.selectList(any())).thenReturn(List.of());

        // 1. 申请退款
        RefundApplyRequest applyReq = RefundApplyRequest.builder()
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .reason("课程内容与预期不符")
                .build();
        RefundDetailResponse applyResp = refundService.applyRefund(2091648316809035778L, applyReq);
        assertEquals(RefundStatus.APPLIED, applyResp.getStatus());

        // 2. 审核退款
        PaymentRefundEntity refundEntity = PaymentRefundEntity.builder()
                .id(2091999912345678902L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(RefundStatus.APPLIED)
                .build();

        when(refundMapper.selectById(2091999912345678902L)).thenReturn(refundEntity);
        when(refundMapper.updateStatusCas(eq(2091999912345678902L), eq(RefundStatus.PROCESSING), eq(RefundStatus.SUCCESS), any(), any()))
                .thenReturn(1);

        RefundAuditRequest auditReq = RefundAuditRequest.builder()
                .approve(true)
                .remark("审核通过并原路退回")
                .build();
        RefundDetailResponse auditResp = refundService.auditRefund(1L, 2091999912345678902L, auditReq);
        assertEquals(RefundStatus.SUCCESS, auditResp.getStatus());
    }

    @Test
    @DisplayName("Stage 8 & 9: 日终对账差错识别与人工平账")
    void testStage8And9_reconciliationFlow() {
        LocalDate date = LocalDate.of(2026, 8, 25);

        // 本地有 1 笔 19900 订单
        PaymentOrderEntity localOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.SUCCESS)
                .channelTradeNo("TR_001")
                .createdAt(date.atTime(12, 0))
                .deleted(0)
                .build();
        when(paymentOrderMapper.selectList(any())).thenReturn(List.of(localOrder));

        ReconciliationBatchResponse batchResp = reconciliationService.runReconciliation(date, PaymentChannel.MOCK);
        assertNotNull(batchResp);

        // 平账
        ReconciliationDiffEntity diff = ReconciliationDiffEntity.builder()
                .id(9001L)
                .batchId(batchResp.getId())
                .diffType(DiffType.AMOUNT_MISMATCH)
                .resolveStatus(ResolveStatus.UNRESOLVED)
                .build();
        when(diffMapper.selectById(9001L)).thenReturn(diff);
        when(diffMapper.selectCount(any())).thenReturn(0L);
        when(batchMapper.selectById(any())).thenReturn(
                ReconciliationBatchEntity.builder().id(batchResp.getId()).status(ReconciliationBatchStatus.DIFF_FOUND).build());

        ReconcileDiffResolveRequest resolveReq = ReconcileDiffResolveRequest.builder()
                .action(ResolveAction.MANUAL_REPAIR)
                .remark("平账完成")
                .build();
        ReconciliationDiffResponse diffResp = reconciliationService.resolveDiff(1L, 9001L, resolveReq);
        assertEquals(ResolveStatus.RESOLVED, diffResp.getResolveStatus());
    }
}
