package com.educloud.order.exception;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.ErrorDetails;

public class OrderBizException extends BusinessException {

    public OrderBizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public OrderBizException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public OrderBizException(ErrorCode errorCode, String message, ErrorDetails details) {
        super(errorCode, message, details);
    }

    public OrderBizException(ErrorCode errorCode, String message, ErrorDetails details, Throwable cause) {
        super(errorCode, message, details, cause);
    }
}
