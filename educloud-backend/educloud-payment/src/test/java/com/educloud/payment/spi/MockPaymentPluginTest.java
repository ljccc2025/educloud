package com.educloud.payment.spi;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.RefundStatus;
import com.educloud.payment.enums.TradeType;
import com.educloud.payment.spi.model.CallbackVerifyResult;
import com.educloud.payment.spi.model.ChannelBillItem;
import com.educloud.payment.spi.model.PaymentContext;
import com.educloud.payment.spi.model.RefundContext;
import com.educloud.payment.spi.model.UnifiedPayResult;
import com.educloud.payment.spi.model.UnifiedQueryResult;
import com.educloud.payment.spi.model.UnifiedRefundResult;
import com.educloud.payment.spi.plugins.MockPaymentPlugin;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPaymentPluginTest {

    private final MockPaymentPlugin plugin = new MockPaymentPlugin();

    @Test
    void initiatePayment_returnsMockDetails() {
        PaymentContext context = PaymentContext.builder()
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .userId(2091648316809035778L)
                .amountCents(19900L)
                .currency("CNY")
                .channel(PaymentChannel.MOCK)
                .tradeType(TradeType.NATIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        UnifiedPayResult result = plugin.initiatePayment(context);
        assertTrue(result.isSuccess());
        assertEquals(PaymentStatus.PAYING, result.getStatus());
        assertEquals("MOCK_TR_2091998812345678901", result.getChannelTradeNo());
        assertNotNull(result.getPayUrl());
        assertNotNull(result.getQrCode());
    }

    @Test
    void verifyAndParseCallback_parsesJsonCorrectly() {
        String json = "{\"paymentOrderId\":2091998812345678901,\"amountCents\":19900,\"notifyId\":\"NOTIFY_001\",\"channelTradeNo\":\"TR_001\"}";
        CallbackVerifyResult result = plugin.verifyAndParseCallback(Map.of(), Map.of(), json);

        assertTrue(result.isValid());
        assertEquals(2091998812345678901L, result.getPaymentOrderId());
        assertEquals(19900L, result.getAmountCents());
        assertEquals("NOTIFY_001", result.getNotifyId());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
    }

    @Test
    void initiateRefund_returnsSuccess() {
        RefundContext context = RefundContext.builder()
                .refundId(2091999912345678902L)
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .refundAmountCents(19900L)
                .reason("不想学了")
                .channel(PaymentChannel.MOCK)
                .build();

        UnifiedRefundResult result = plugin.initiateRefund(context);
        assertTrue(result.isSuccess());
        assertEquals(RefundStatus.SUCCESS, result.getStatus());
        assertEquals("MOCK_REF_2091999912345678902", result.getChannelRefundNo());
    }

    @Test
    void queryAndDownloadBill_succeeds() {
        UnifiedQueryResult queryResult = plugin.queryPayment("MOCK_TR_001", "2091998812345678901");
        assertTrue(queryResult.isSuccess());
        assertEquals(PaymentStatus.SUCCESS, queryResult.getStatus());

        List<ChannelBillItem> bills = plugin.downloadBill(LocalDate.now());
        assertNotNull(bills);
        assertEquals(1, bills.size());
    }
}
