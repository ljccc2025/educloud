package com.educloud.common.error;

public interface ErrorCode {

    String code();

    int httpStatus();

    String defaultMessage();
}
