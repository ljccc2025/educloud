package com.educloud.payment.service;

import com.educloud.common.api.ApiResponse;
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
import com.educloud.payment.feign.OrderClient;
import com.educloud.payment.feign.dto.OrderPayableSnapshotResponse;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.PaymentTransactionMapper;
import com.educloud.payment.messaging.OutboxEventWriter;
import com.educloud.payment.service.impl.PaymentServiceImpl;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.plugins.MockPaymentPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private PaymentTransactionMapper paymentTransactionMapper;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private OrderClient orderClient;

    private PaymentProperties properties;
    private PaymentChannelFactory channelFactory;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties(
                "local",
                null,
                new PaymentProperties.InternalProperties("gateway", "educloud-payment", "secret"),
                null,
                null
        );
        channelFactory = new PaymentChannelFactory(List.of(new MockPaymentPlugin()));
        paymentService = new PaymentServiceImpl(
                paymentOrderMapper,
                paymentTransactionMapper,
                channelFactory,
                outboxEventWriter,
                orderClient,
                properties
        );
    }

    @Test
    void createCashierPayment_success() {
        OrderPayableSnapshotResponse snapshot = OrderPayableSnapshotResponse.builder()
                .orderId(2091895618182258690L)
                .orderNo("ORD20260825001")
                .studentId(2091648316809035778L)
                .status("PENDING_PAYMENT")
                .payableAmount(new BigDecimal("199.00"))
                .currency("CNY")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        ApiResponse<OrderPayableSnapshotResponse> snapshotResp = new ApiResponse<>(
                "SUCCESS", "OK", snapshot, "req-1", Instant.now());

        when(orderClient.getPayableSnapshot(eq(2091895618182258690L), any())).thenReturn(snapshotResp);
        when(paymentOrderMapper.selectOne(any())).thenReturn(null);

        CashierPayRequest request = CashierPayRequest.builder()
                .orderId(2091895618182258690L)
                .channelCode(PaymentChannel.MOCK)
                .tradeType(TradeType.NATIVE)
                .build();

        CashierPayResponse response = paymentService.createCashierPayment(2091648316809035778L, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.PAYING, response.getStatus());
        assertEquals(19900L, response.getAmountCents());
        assertNotNull(response.getPayUrl());
        verify(paymentOrderMapper).insert(any(PaymentOrderEntity.class));
        verify(paymentTransactionMapper).insert(any(PaymentTransactionEntity.class));
    }

    @Test
    void createCashierPayment_orderExpired_throwsException() {
        OrderPayableSnapshotResponse snapshot = OrderPayableSnapshotResponse.builder()
                .orderId(2091895618182258690L)
                .orderNo("ORD20260825001")
                .studentId(2091648316809035778L)
                .status("PENDING_PAYMENT")
                .payableAmount(new BigDecimal("199.00"))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        ApiResponse<OrderPayableSnapshotResponse> snapshotResp = new ApiResponse<>(
                "SUCCESS", "OK", snapshot, "req-1", Instant.now());

        when(orderClient.getPayableSnapshot(eq(2091895618182258690L), any())).thenReturn(snapshotResp);

        CashierPayRequest request = CashierPayRequest.builder()
                .orderId(2091895618182258690L)
                .channelCode(PaymentChannel.MOCK)
                .build();

        assertThrows(PaymentBizException.class, () -> paymentService.createCashierPayment(2091648316809035778L, request));
    }

    @Test
    void mockConfirmPayment_success() {
        PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .userId(2091648316809035778L)
                .amountCents(19900L)
                .currency("CNY")
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.PAYING)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectById(2091998812345678901L)).thenReturn(paymentOrder);
        when(paymentOrderMapper.updateStatusToSuccessCas(eq(2091998812345678901L), eq(PaymentStatus.SUCCESS), any(), any(), any()))
                .thenReturn(1);

        PaymentDetailResponse response = paymentService.mockConfirmPayment(2091648316809035778L, 2091998812345678901L);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(outboxEventWriter).writeEvent(eq("PAYMENT"), eq(2091998812345678901L), eq("PaymentSucceededEvent"), any());
    }

    @Test
    void mockConfirmPayment_prodEnv_throwsException() {
        PaymentProperties prodProps = new PaymentProperties(
                "prod", null, null, null, null
        );
        PaymentServiceImpl prodService = new PaymentServiceImpl(
                paymentOrderMapper, paymentTransactionMapper, channelFactory, outboxEventWriter, orderClient, prodProps
        );

        assertThrows(PaymentBizException.class, () -> prodService.mockConfirmPayment(2091648316809035778L, 2091998812345678901L));
    }
}
