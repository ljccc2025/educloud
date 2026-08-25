package com.educloud.live.exception;

import com.educloud.common.error.ErrorCode;
import lombok.Getter;

@Getter
public class LiveException extends RuntimeException {

    private final ErrorCode errorCode;

    public LiveException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public LiveException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LiveException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
