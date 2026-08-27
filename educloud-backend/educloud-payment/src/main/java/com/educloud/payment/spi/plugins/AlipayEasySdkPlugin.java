package com.educloud.payment.spi.plugins;

import com.educloud.payment.config.PaymentProperties;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.RefundStatus;
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.CallbackVerifyResult;
import com.educloud.payment.spi.model.ChannelBillItem;
import com.educloud.payment.spi.model.PaymentContext;
import com.educloud.payment.spi.model.RefundContext;
import com.educloud.payment.spi.model.UnifiedPayResult;
import com.educloud.payment.spi.model.UnifiedQueryResult;
import com.educloud.payment.spi.model.UnifiedRefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayEasySdkPlugin implements PaymentChannelPlugin {

    private final PaymentProperties properties;

    @Override
    public PaymentChannel getChannel() {
        return PaymentChannel.ALIPAY;
    }

    @Override
    public UnifiedPayResult initiatePayment(PaymentContext context) {
        String gatewayHost = properties.alipay() != null && properties.alipay().gatewayHost() != null
                ? properties.alipay().gatewayHost()
                : "openapi-sandbox.dl.alipaydev.com";

        String channelTradeNo = "ALI_TR_" + context.getPaymentOrderId();
        BigDecimal amountYuan = BigDecimal.valueOf(context.getAmountCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        String payUrl = String.format("https://%s/gateway.do?app_id=%s&out_trade_no=%s&total_amount=%s&subject=%s",
                gatewayHost,
                properties.alipay() != null ? properties.alipay().appId() : "alipay_mock_app",
                context.getPaymentOrderId(),
                amountYuan,
                context.getSubject() != null ? context.getSubject() : "EduCloud Course");

        String qrCode = "https://qr.alipay.com/bax" + context.getPaymentOrderId();

        return UnifiedPayResult.builder()
                .success(true)
                .status(PaymentStatus.PAYING)
                .channelTradeNo(channelTradeNo)
                .payUrl(payUrl)
                .qrCode(qrCode)
                .rawResponse("{\"gateway\":\"" + gatewayHost + "\",\"outTradeNo\":\"" + context.getPaymentOrderId() + "\"}")
                .build();
    }

    @Override
    public CallbackVerifyResult verifyAndParseCallback(Map<String, String> headers, Map<String, String> params, String rawBody) {
        if (params == null || params.isEmpty()) {
            return CallbackVerifyResult.builder()
                    .valid(false)
                    .errorMessage("Alipay callback parameters cannot be empty")
                    .build();
        }

        try {
            String sign = params.get("sign");
            String signType = params.getOrDefault("sign_type", "RSA2");
            String outTradeNoStr = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String totalAmountStr = params.get("total_amount");
            String tradeStatus = params.get("trade_status");
            String notifyId = params.getOrDefault("notify_id", "ALI_NOTIFY_" + System.currentTimeMillis());

            Long paymentOrderId = outTradeNoStr != null ? Long.parseLong(outTradeNoStr) : null;
            // 金额修复（M08 审查）：元→分四舍五入后精确取整，避免 longValue() 静默截断。
            Long amountCents = totalAmountStr != null
                    ? new BigDecimal(totalAmountStr).multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP).longValueExact()
                    : null;

            boolean signValid = verifyRsa2Sign(params, sign, properties.alipay() != null ? properties.alipay().alipayPublicKey() : null);

            PaymentStatus status = ("TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "TRADE_FINISHED".equalsIgnoreCase(tradeStatus))
                    ? PaymentStatus.SUCCESS
                    : PaymentStatus.PAYING;

            return CallbackVerifyResult.builder()
                    .valid(signValid)
                    .paymentOrderId(paymentOrderId)
                    .notifyId(notifyId)
                    .channelTradeNo(tradeNo != null ? tradeNo : "ALI_TR_" + paymentOrderId)
                    .amountCents(amountCents)
                    .status(status)
                    .paidAt(LocalDateTime.now())
                    .rawPayload(params.toString())
                    .responseMessage("success")
                    .build();
        } catch (Exception e) {
            log.error("Failed to verify and parse Alipay callback: {}", e.getMessage(), e);
            return CallbackVerifyResult.builder()
                    .valid(false)
                    .errorMessage("Failed to verify Alipay callback: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public UnifiedRefundResult initiateRefund(RefundContext context) {
        String channelRefundNo = "ALI_REF_" + context.getRefundId();
        return UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.SUCCESS)
                .channelRefundNo(channelRefundNo)
                .refundAmountCents(context.getRefundAmountCents())
                .refundedAt(LocalDateTime.now())
                .rawResponse("{\"code\":\"10000\",\"msg\":\"Success\",\"trade_no\":\"" + context.getChannelTradeNo() + "\"}")
                .build();
    }

    @Override
    public UnifiedRefundResult queryRefund(RefundContext context) {
        // P2-7 修复：退款查单消歧。沙箱模拟渠道已退；接入真实渠道后调 alipay.trade.refund.query，
        // 按 refund_status 映射：REFUND_SUCCESS→SUCCESS，REFUND_CLOSED→FAILED，其余二义。
        String channelRefundNo = "ALI_REF_" + context.getRefundId();
        return UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.SUCCESS)
                .channelRefundNo(channelRefundNo)
                .refundAmountCents(context.getRefundAmountCents())
                .refundedAt(LocalDateTime.now())
                .rawResponse("{\"code\":\"10000\",\"msg\":\"Success\",\"refund_status\":\"REFUND_SUCCESS\"}")
                .build();
    }

    @Override
    public UnifiedQueryResult queryPayment(String channelTradeNo, String paymentOrderId) {
        return UnifiedQueryResult.builder()
                .success(true)
                .status(PaymentStatus.SUCCESS)
                .channelTradeNo(channelTradeNo != null ? channelTradeNo : "ALI_TR_" + paymentOrderId)
                .paidAt(LocalDateTime.now())
                .rawResponse("{\"code\":\"10000\",\"msg\":\"Success\",\"trade_status\":\"TRADE_SUCCESS\"}")
                .build();
    }

    @Override
    public List<ChannelBillItem> downloadBill(LocalDate date) {
        // 对账口径修复（M08 审查）：原固定桩单任意日期对账必出 CHANNEL_MORE 假差错。
        // 沙箱无真实账单，返回空列表；接入真实渠道后下载并按交易日返回账单。
        return List.of();
    }

    private boolean verifyRsa2Sign(Map<String, String> params, String sign, String publicKeyStr) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        String env = properties.environment();
        boolean isProd = "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env);
        if (publicKeyStr == null || publicKeyStr.isBlank() || publicKeyStr.startsWith("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...")) {
            if (isProd) {
                log.error("Alipay public key is not properly configured in production environment!");
                return false;
            }
            return "test_valid_sign".equals(sign) || sign.length() > 20;
        }

        try {
            Map<String, String> sortedParams = new TreeMap<>(params);
            sortedParams.remove("sign");
            sortedParams.remove("sign_type");

            StringBuilder content = new StringBuilder();
            for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    if (content.length() > 0) {
                        content.append("&");
                    }
                    content.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            byte[] keyBytes = Base64.getDecoder().decode(publicKeyStr.replaceAll("\\s+", ""));
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(content.toString().getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.warn("Alipay RSA2 verification failed: {}", e.getMessage());
            return false;
        }
    }
}
