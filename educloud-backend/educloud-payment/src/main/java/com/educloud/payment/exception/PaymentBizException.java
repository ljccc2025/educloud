package com.educloud.payment.exception;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.ErrorDetails;

public class PaymentBizException extends BusinessException {

    public PaymentBizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PaymentBizException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public PaymentBizException(ErrorCode errorCode, String message, ErrorDetails details) {
        super(errorCode, message, details);
    }

    public PaymentBizException(ErrorCode errorCode, String message, ErrorDetails details, Throwable cause) {
        super(errorCode, message, details, cause);
    }
}
