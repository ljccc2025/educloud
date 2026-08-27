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

    /**
     * 退款查单（P2-7 消歧）：确认退款在渠道侧是否已成功/失败。
     * 返回语义：
     * <ul>
     *   <li>success=true 且 status=SUCCESS → 渠道确认已退（可收敛为系统 SUCCESS）</li>
     *   <li>success=true 且 status=FAILED → 渠道确认未退（可收敛为系统 FAILED，允许重新审核）</li>
     *   <li>其余（success=false 或 status 为 APPLIED/PROCESSING 等）→ 结果未知/二义，保持 PROCESSING 由定时任务收敛</li>
     * </ul>
     */
    UnifiedRefundResult queryRefund(RefundContext context);

    UnifiedQueryResult queryPayment(String channelTradeNo, String paymentOrderId);

    List<ChannelBillItem> downloadBill(LocalDate date);
}
