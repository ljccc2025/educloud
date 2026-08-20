package com.educloud.common.api;

import com.educloud.common.error.ErrorCode;
import com.educloud.common.web.RequestContextAccessor;
import java.time.Clock;
import java.util.Objects;

public final class ApiResponseFactory {

    private final RequestContextAccessor requestContext;
    private final Clock clock;

    public ApiResponseFactory(RequestContextAccessor requestContext, Clock clock) {
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                "SUCCESS",
                "OK",
                data,
                requestContext.requestId(),
                clock.instant());
    }

    public <T> ApiResponse<T> error(ErrorCode code, String message, T details) {
        Objects.requireNonNull(code, "code");
        return new ApiResponse<>(
                code.code(),
                Objects.requireNonNull(message, "message"),
                details,
                requestContext.requestId(),
                clock.instant());
    }
}
