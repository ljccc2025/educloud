package com.educloud.payment.spi.plugins;

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
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MockPaymentPlugin implements PaymentChannelPlugin {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        List<ChannelBillItem> list = new ArrayList<>();
        list.add(ChannelBillItem.builder()
                .channelTradeNo("MOCK_TR_BILL_001")
                .paymentOrderId(9000000000000000801L)
                .amountCents(19900L)
                .feeCents(0L)
                .status(PaymentStatus.SUCCESS)
                .tradeType("MOCK")
                .tradeTime(date.atTime(10, 0, 0))
                .build());
        return list;
    }
}
