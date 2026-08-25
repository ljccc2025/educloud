package com.educloud.payment.spi;

import com.educloud.payment.config.PaymentProperties;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.TradeType;
import com.educloud.payment.spi.model.CallbackVerifyResult;
import com.educloud.payment.spi.model.PaymentContext;
import com.educloud.payment.spi.model.UnifiedPayResult;
import com.educloud.payment.spi.plugins.WeChatPayV3Plugin;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeChatPayV3PluginTest {

    private final PaymentProperties properties = new PaymentProperties(
            "local",
            null,
            null,
            null,
            new PaymentProperties.WechatProperties(
                    "wx8888888888888888",
                    "1900000109",
                    "abcdef0123456789abcdef0123456789",
                    "7054B2A...",
                    "/tmp/apiclient_key.pem",
                    "http://localhost:8080/api/v1/payment-callbacks/WECHAT"
            )
    );

    private final WeChatPayV3Plugin plugin = new WeChatPayV3Plugin(properties);

    @Test
    void initiatePayment_returnsWeChatCodeUrl() {
        PaymentContext context = PaymentContext.builder()
                .paymentOrderId(2091998812345678901L)
                .orderId(2091895618182258690L)
                .amountCents(19900L)
                .channel(PaymentChannel.WECHAT)
                .tradeType(TradeType.NATIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        UnifiedPayResult result = plugin.initiatePayment(context);
        assertTrue(result.isSuccess());
        assertEquals(PaymentStatus.PAYING, result.getStatus());
        assertTrue(result.getQrCode().startsWith("weixin://wxpay/bizpayurl"));
    }

    @Test
    void verifyAndParseCallback_plainJson_succeeds() {
        String rawBody = "{\"id\":\"WX_NOTIFY_001\",\"out_trade_no\":2091998812345678901,\"transaction_id\":\"WX_TR_999\",\"trade_state\":\"SUCCESS\",\"amount\":{\"total\":19900}}";

        CallbackVerifyResult result = plugin.verifyAndParseCallback(Map.of(), Map.of(), rawBody);
        assertTrue(result.isValid());
        assertEquals(2091998812345678901L, result.getPaymentOrderId());
        assertEquals(19900L, result.getAmountCents());
        assertEquals("WX_TR_999", result.getChannelTradeNo());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
    }
}
