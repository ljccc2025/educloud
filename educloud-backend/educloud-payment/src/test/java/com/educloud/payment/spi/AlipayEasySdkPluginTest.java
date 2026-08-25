package com.educloud.payment.spi;

import com.educloud.payment.config.PaymentProperties;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.TradeType;
import com.educloud.payment.spi.model.CallbackVerifyResult;
import com.educloud.payment.spi.model.PaymentContext;
import com.educloud.payment.spi.model.UnifiedPayResult;
import com.educloud.payment.spi.plugins.AlipayEasySdkPlugin;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlipayEasySdkPluginTest {

    private final PaymentProperties properties = new PaymentProperties(
            "local",
            null,
            null,
            new PaymentProperties.AlipayProperties(
                    "2021000000000001",
                    "mock_priv_key",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
                    "http://localhost:8080/api/v1/payment-callbacks/ALIPAY",
                    "openapi-sandbox.dl.alipaydev.com"
            ),
            null
    );

    private final AlipayEasySdkPlugin plugin = new AlipayEasySdkPlugin(properties);

    @Test
    void initiatePayment_returnsAlipayUrlAndQr() {
        PaymentContext context = PaymentContext.builder()
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channel(PaymentChannel.ALIPAY)
                .tradeType(TradeType.PAGE)
                .subject("Java Spring Microservice Course")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        UnifiedPayResult result = plugin.initiatePayment(context);
        assertTrue(result.isSuccess());
        assertEquals(PaymentStatus.PAYING, result.getStatus());
        assertTrue(result.getPayUrl().contains("openapi-sandbox.dl.alipaydev.com"));
        assertTrue(result.getQrCode().contains("qr.alipay.com"));
    }

    @Test
    void verifyAndParseCallback_succeeds() {
        Map<String, String> params = Map.of(
                "out_trade_no", "2091998812345678901",
                "trade_no", "ALI_202608250001",
                "total_amount", "199.00",
                "trade_status", "TRADE_SUCCESS",
                "sign", "test_valid_sign"
        );

        CallbackVerifyResult result = plugin.verifyAndParseCallback(Map.of(), params, null);
        assertTrue(result.isValid());
        assertEquals(2091998812345678901L, result.getPaymentOrderId());
        assertEquals(19900L, result.getAmountCents());
        assertEquals("ALI_202608250001", result.getChannelTradeNo());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals("success", result.getResponseMessage());
    }
}
