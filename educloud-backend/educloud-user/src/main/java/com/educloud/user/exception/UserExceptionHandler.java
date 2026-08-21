package com.educloud.user.exception;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.CommonErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法安全异常转换：@PreAuthorize 抛出的 AccessDeniedException 发生在 DispatcherServlet 内，
 * ExceptionTranslationFilter 无法捕获，需在此转换为 403（API 规范第 4 节）。
 */
@RestControllerAdvice
public final class UserExceptionHandler {

    private final ApiResponseFactory responses;

    public UserExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(responses.error(
                        CommonErrorCode.ACCESS_DENIED,
                        CommonErrorCode.ACCESS_DENIED.defaultMessage(),
                        null));
    }
}
