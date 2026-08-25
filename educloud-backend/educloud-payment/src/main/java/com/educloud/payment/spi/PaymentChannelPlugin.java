package com.educloud.payment.spi;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.spi.model.CallbackVerifyResult;
import com.educloud.payment.spi.model.ChannelBillItem;
import com.educloud.payment.spi.model.PaymentContext;
import com.educloud.payment.spi.model.RefundContext;
import com.educloud.payment.spi.model.UnifiedPayResult;
import com.educloud.payment.spi.model.UnifiedQueryResult;
import com.educloud.payment.spi.model.UnifiedRefundResult;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface PaymentChannelPlugin {

    PaymentChannel getChannel();

    UnifiedPayResult initiatePayment(PaymentContext context);

    CallbackVerifyResult verifyAndParseCallback(Map<String, String> headers, Map<String, String> params, String rawBody);

    UnifiedRefundResult initiateRefund(RefundContext context);

    UnifiedQueryResult queryPayment(String channelTradeNo, String paymentOrderId);

    List<ChannelBillItem> downloadBill(LocalDate date);
}
