package com.educloud.notification.exception;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.ErrorCode;

public class NotificationBizException extends BusinessException {

    public NotificationBizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotificationBizException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
