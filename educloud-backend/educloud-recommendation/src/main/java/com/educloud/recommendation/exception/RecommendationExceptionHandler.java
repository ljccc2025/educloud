package com.educloud.recommendation.exception;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.FieldViolation;
import com.educloud.common.error.ValidationErrorDetails;
import com.educloud.common.web.RequestContext;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 推荐服务域异常出口（M13 任务 7）。
 *
 * <p>把模块异常映射为 {@link ErrorCode}（RecommendationErrorCode 或通用
 * CommonErrorCode）的 ApiResponse 信封响应，不向客户端泄漏内部细节。与 common 的
 * {@code GlobalExceptionHandler} 并存：显式声明 {@code @Order(Ordered.LOWEST_PRECEDENCE - 1)}
 * 先于未声明顺序的 common advice 执行（复制 educloud-course CourseExceptionHandler
 * 模式，避免 common 的 Exception 兜底抢先吞掉本域异常）。本 advice 不声明 Exception
 * 兜底，未知异常 500 仍由 common 处理。</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public final class RecommendationExceptionHandler {

    private final ApiResponseFactory responses;

    public RecommendationExceptionHandler(ApiResponseFactory responses) {
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    /** BusinessException 直通其 message（默认即 defaultMessage）。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return respond(exception.errorCode(), exception.getMessage(), exception.details());
    }

    /** 方法级安全/权限校验（@PreAuthorize 等）拒绝 → 403。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return respond(
                CommonErrorCode.ACCESS_DENIED,
                CommonErrorCode.ACCESS_DENIED.defaultMessage(),
                null);
    }

    /** @Valid 请求体验证失败 → 400 VALIDATION_FAILED（复用 common 的 FieldViolation 结构）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(RecommendationExceptionHandler::toViolation)
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(violations));
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(ErrorCode code, String message, T details) {
        ApiResponse<T> body = responses.error(code, message, details);
        return ResponseEntity.status(code.httpStatus())
                .header(RequestContext.REQUEST_ID_HEADER, body.requestId())
                .body(body);
    }

    private static FieldViolation toViolation(FieldError error) {
        String code = error.getCode() == null ? "Invalid" : error.getCode();
        String message = error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage();
        return new FieldViolation(error.getField(), code, message);
    }
}
