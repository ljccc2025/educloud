package com.educloud.payment.exception;

import com.educloud.common.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_ORDER_NOT_FOUND(404, 404801, "支付单不存在"),
    PAYMENT_EXPIRED(400, 400801, "支付单已超时关闭"),
    PAYMENT_STATUS_INVALID(400, 400802, "支付单状态不支持当前操作"),
    AMOUNT_MISMATCH(400, 400803, "支付金额与订单应付金额不一致"),
    SIGN_VERIFY_FAILED(400, 400804, "支付渠道签名验证失败"),
    DUPLICATE_PAYMENT(409, 409801, "支付单已完成，请勿重复支付"),
    REFUND_NOT_ALLOWED(400, 400805, "订单未支付或当前状态不允许发起退款"),
    REFUND_AMOUNT_EXCEEDED(400, 400806, "退款金额超过最大可退金额"),
    REFUND_NOT_FOUND(404, 404802, "退款单不存在"),
    REFUND_STATUS_INVALID(400, 400807, "退款单状态不支持该审核操作"),
    CHANNEL_NOT_SUPPORTED(400, 400808, "暂不支持的支付渠道"),
    CHANNEL_INVOKE_FAILED(500, 500801, "调用第三方支付渠道通信异常"),
    RECONCILIATION_BATCH_NOT_FOUND(404, 404803, "对账批次不存在"),
    RECONCILIATION_DIFF_NOT_FOUND(404, 404804, "对账差错单不存在"),
    RECONCILIATION_DIFF_ALREADY_RESOLVED(400, 400809, "该差错单已平账处理"),
    MOCK_PAY_DISABLED(403, 403801, "当前生产环境禁用 Mock 模拟支付");

    private final int httpStatus;
    private final int businessCode;
    private final String defaultMessage;

    PaymentErrorCode(int httpStatus, int businessCode, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.businessCode = businessCode;
        this.defaultMessage = defaultMessage;
    }

    public int getBusinessCode() {
        return businessCode;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
