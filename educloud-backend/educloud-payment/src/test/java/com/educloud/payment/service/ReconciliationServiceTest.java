package com.educloud.payment.service;

import com.educloud.payment.dto.request.ReconcileDiffResolveRequest;
import com.educloud.payment.dto.response.ReconciliationBatchResponse;
import com.educloud.payment.dto.response.ReconciliationDiffResponse;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.ReconciliationBatchEntity;
import com.educloud.payment.entity.ReconciliationDiffEntity;
import com.educloud.payment.enums.DiffType;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.ReconciliationBatchStatus;
import com.educloud.payment.enums.ResolveAction;
import com.educloud.payment.enums.ResolveStatus;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.ReconciliationBatchMapper;
import com.educloud.payment.mapper.ReconciliationDiffMapper;
import com.educloud.payment.service.impl.ReconciliationServiceImpl;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.ChannelBillItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private ReconciliationBatchMapper batchMapper;

    @Mock
    private ReconciliationDiffMapper diffMapper;

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private PaymentChannelFactory channelFactory;

    @Mock
    private PaymentChannelPlugin mockPlugin;

    private ReconciliationServiceImpl reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationServiceImpl(
                batchMapper,
                diffMapper,
                paymentOrderMapper,
                channelFactory
        );
    }

    @Test
    void runReconciliation_diffFound() {
        LocalDate date = LocalDate.of(2026, 8, 25);
        when(channelFactory.getPlugin(PaymentChannel.MOCK)).thenReturn(mockPlugin);

        PaymentOrderEntity localOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.SUCCESS)
                .channelTradeNo("TR_001")
                .createdAt(date.atTime(10, 0))
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectList(any())).thenReturn(List.of(localOrder));

        ChannelBillItem billItem = ChannelBillItem.builder()
                .paymentOrderId(2091998812345678901L)
                .channelTradeNo("TR_001")
                .amountCents(18000L) // 金额不一致
                .status(PaymentStatus.SUCCESS)
                .build();

        when(mockPlugin.downloadBill(date)).thenReturn(List.of(billItem));

        ReconciliationBatchResponse response = reconciliationService.runReconciliation(date, PaymentChannel.MOCK);

        assertNotNull(response);
        assertEquals(ReconciliationBatchStatus.DIFF_FOUND, response.getStatus());
        assertEquals(1, response.getDiffCount());
        verify(diffMapper).insert(any(ReconciliationDiffEntity.class));
    }

    @Test
    void runReconciliation_matched() {
        LocalDate date = LocalDate.of(2026, 8, 25);
        when(channelFactory.getPlugin(PaymentChannel.MOCK)).thenReturn(mockPlugin);

        PaymentOrderEntity localOrder = PaymentOrderEntity.builder()
                .id(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channelCode(PaymentChannel.MOCK)
                .status(PaymentStatus.SUCCESS)
                .channelTradeNo("TR_001")
                .createdAt(date.atTime(10, 0))
                .deleted(0)
                .build();

        when(paymentOrderMapper.selectList(any())).thenReturn(List.of(localOrder));

        ChannelBillItem billItem = ChannelBillItem.builder()
                .paymentOrderId(2091998812345678901L)
                .channelTradeNo("TR_001")
                .amountCents(19900L) // 完全匹配
                .status(PaymentStatus.SUCCESS)
                .build();

        when(mockPlugin.downloadBill(date)).thenReturn(List.of(billItem));

        ReconciliationBatchResponse response = reconciliationService.runReconciliation(date, PaymentChannel.MOCK);

        assertNotNull(response);
        assertEquals(ReconciliationBatchStatus.MATCHED, response.getStatus());
        assertEquals(0, response.getDiffCount());
    }

    @Test
    void resolveDiff_success() {
        ReconciliationDiffEntity diff = ReconciliationDiffEntity.builder()
                .id(1001L)
                .batchId(2001L)
                .diffType(DiffType.AMOUNT_MISMATCH)
                .paymentOrderId(2091998812345678901L)
                .resolveStatus(ResolveStatus.UNRESOLVED)
                .build();

        when(diffMapper.selectById(1001L)).thenReturn(diff);
        when(diffMapper.selectCount(any())).thenReturn(0L);
        when(batchMapper.selectById(2001L)).thenReturn(
                ReconciliationBatchEntity.builder().id(2001L).status(ReconciliationBatchStatus.DIFF_FOUND).build());

        ReconcileDiffResolveRequest request = ReconcileDiffResolveRequest.builder()
                .action(ResolveAction.MANUAL_REPAIR)
                .remark("人工补录差额")
                .build();

        ReconciliationDiffResponse response = reconciliationService.resolveDiff(1L, 1001L, request);

        assertNotNull(response);
        assertEquals(ResolveStatus.RESOLVED, response.getResolveStatus());
        assertEquals(ResolveAction.MANUAL_REPAIR, response.getResolveAction());
        verify(diffMapper).updateById(any(ReconciliationDiffEntity.class));
    }
}
