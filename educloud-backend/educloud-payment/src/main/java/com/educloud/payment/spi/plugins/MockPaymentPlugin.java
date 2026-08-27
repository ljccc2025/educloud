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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MockPaymentPlugin implements PaymentChannelPlugin {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final PaymentProperties properties;

    private static boolean isProduction(String env) {
        return "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env);
    }

    @Override
    public PaymentChannel getChannel() {
        return PaymentChannel.MOCK;
    }

    @Override
    public UnifiedPayResult initiatePayment(PaymentContext context) {
        String channelTradeNo = "MOCK_TR_" + context.getPaymentOrderId();
        String payUrl = "/mock-pay?paymentOrderId=" + context.getPaymentOrderId();
        String qrCode = "MOCK_QR_" + context.getPaymentOrderId();

        return UnifiedPayResult.builder()
                .success(true)
                .status(PaymentStatus.PAYING)
                .channelTradeNo(channelTradeNo)
                .payUrl(payUrl)
                .qrCode(qrCode)
                .rawResponse("{\"status\":\"MOCK_PAYING\",\"channelTradeNo\":\"" + channelTradeNo + "\"}")
                .build();
    }

    @Override
    public CallbackVerifyResult verifyAndParseCallback(Map<String, String> headers, Map<String, String> params, String rawBody) {
        // 安全修复（M08 审查）：MOCK 回调匿名可达且无签名，生产环境一律拒绝，
        // 与 mockConfirmPayment 的环境门控对齐，堵住“MOCK 渠道零成本置支付成功”资损链。
        if (isProduction(properties != null ? properties.environment() : null)) {
            return CallbackVerifyResult.builder()
                    .valid(false)
                    .errorMessage("MOCK channel callback is disabled in production")
                    .build();
        }
        try {
            Long paymentOrderId = null;
            Long amountCents = null;
            String notifyId = "MOCK_NOTIFY_" + System.currentTimeMillis();
            String channelTradeNo = "MOCK_TR_CALLBACK";
            PaymentStatus status = PaymentStatus.SUCCESS;

            if (rawBody != null && !rawBody.isBlank()) {
                JsonNode node = OBJECT_MAPPER.readTree(rawBody);
                if (node.has("paymentOrderId")) {
                    paymentOrderId = node.get("paymentOrderId").asLong();
                }
                if (node.has("amountCents")) {
                    amountCents = node.get("amountCents").asLong();
                }
                if (node.has("notifyId")) {
                    notifyId = node.get("notifyId").asText();
                }
                if (node.has("channelTradeNo")) {
                    channelTradeNo = node.get("channelTradeNo").asText();
                }
                if (node.has("status") && "FAILED".equalsIgnoreCase(node.get("status").asText())) {
                    status = PaymentStatus.FAILED;
                }
            } else if (params != null) {
                if (params.containsKey("paymentOrderId")) {
                    paymentOrderId = Long.parseLong(params.get("paymentOrderId"));
                }
                if (params.containsKey("amountCents")) {
                    amountCents = Long.parseLong(params.get("amountCents"));
                }
                if (params.containsKey("notifyId")) {
                    notifyId = params.get("notifyId");
                }
                if (params.containsKey("channelTradeNo")) {
                    channelTradeNo = params.get("channelTradeNo");
                }
            }

            return CallbackVerifyResult.builder()
                    .valid(true)
                    .paymentOrderId(paymentOrderId)
                    .notifyId(notifyId)
                    .channelTradeNo(channelTradeNo)
                    .amountCents(amountCents)
                    .status(status)
                    .paidAt(LocalDateTime.now())
                    .rawPayload(rawBody != null ? rawBody : params != null ? params.toString() : "")
                    .responseMessage("{\"code\":\"SUCCESS\",\"message\":\"MOCK_CALLBACK_OK\"}")
                    .build();
        } catch (Exception e) {
            return CallbackVerifyResult.builder()
                    .valid(false)
                    .errorMessage("Failed to parse mock callback: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public UnifiedRefundResult initiateRefund(RefundContext context) {
        String channelRefundNo = "MOCK_REF_" + context.getRefundId();
        return UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.SUCCESS)
                .channelRefundNo(channelRefundNo)
                .refundAmountCents(context.getRefundAmountCents())
                .refundedAt(LocalDateTime.now())
                .rawResponse("{\"status\":\"MOCK_REFUNDED\",\"refundNo\":\"" + channelRefundNo + "\"}")
                .build();
    }

    @Override
    public UnifiedRefundResult queryRefund(RefundContext context) {
        // P2-7 修复：退款查单消歧。沙箱模拟渠道已退（与 initiateRefund 一致，按 refundId 幂等）；
        // 接入真实渠道后改为调用渠道退款查询接口，并按返回码映射：确认已退→SUCCESS，
        // 确认未退→FAILED，查单失败→success=false（二义，由定时任务保持 PROCESSING 重试）。
        String channelRefundNo = "MOCK_REF_" + context.getRefundId();
        return UnifiedRefundResult.builder()
                .success(true)
                .status(RefundStatus.SUCCESS)
                .channelRefundNo(channelRefundNo)
                .refundAmountCents(context.getRefundAmountCents())
                .refundedAt(LocalDateTime.now())
                .rawResponse("{\"status\":\"MOCK_REFUNDED\",\"refundNo\":\"" + channelRefundNo + "\"}")
                .build();
    }

    @Override
    public UnifiedQueryResult queryPayment(String channelTradeNo, String paymentOrderId) {
        return UnifiedQueryResult.builder()
                .success(true)
                .status(PaymentStatus.SUCCESS)
                .channelTradeNo(channelTradeNo != null ? channelTradeNo : "MOCK_TR_" + paymentOrderId)
                .paidAt(LocalDateTime.now())
                .rawResponse("{\"status\":\"MOCK_SUCCESS\"}")
                .build();
    }

    @Override
    public List<ChannelBillItem> downloadBill(LocalDate date) {
        // 对账口径修复（M08 审查）：原固定桩单（paymentOrderId=9000000000000000801）
        // 任意日期对账必出 CHANNEL_MORE 假差错。沙箱无真实账单，返回空列表；
        // 接入真实渠道后在此下载并按交易日返回账单。
        return List.of();
    }
}
