package com.educloud.order.exception;

import com.educloud.common.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {
    CART_EMPTY(400, 400701, "购物车为空，无法结算"),
    CART_ITEM_NOT_FOUND(404, 404701, "购物车商品不存在"),
    COURSE_NOT_ON_SALE(400, 400702, "课程未上架或不可购买"),
    COURSE_ALREADY_ENROLLED(400, 400703, "您已选购过该课程，无需重复购买"),
    DUPLICATE_ORDER_SUBMISSION(409, 409701, "订单正在处理中，请勿重复提交"),
    ORDER_NOT_FOUND(404, 404702, "订单不存在"),
    ORDER_STATUS_INVALID(400, 400704, "订单状态不满足当前操作要求"),
    ORDER_EXPIRED(400, 400705, "订单已超时关闭"),
    ORDER_NOT_OWNED(403, 403701, "无权访问该订单"),
    ORDER_ACCESS_DENIED(403, 403702, "无权访问该订单资源"),
    REFUND_REQUEST_NOT_FOUND(404, 404703, "退款申请不存在"),
    REFUND_AMOUNT_EXCEEDED(400, 400706, "申请退款金额超过最大可退金额"),
    REFUND_STATUS_INVALID(400, 400707, "当前退款申请状态不支持该审核操作");

    private final int httpStatus;
    private final int businessCode;
    private final String defaultMessage;

    OrderErrorCode(int httpStatus, int businessCode, String defaultMessage) {
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
