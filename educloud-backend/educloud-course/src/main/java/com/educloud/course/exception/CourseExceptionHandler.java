package com.educloud.course.exception;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Course 服务统一异常出口（M05 任务 5）。
 *
 * <p>把模块异常映射为 {@link ErrorCode}（CourseErrorCode 或通用 CommonErrorCode）的
 * ApiResponse 信封响应（code/message/data/requestId/timestamp + X-Request-Id 响应头），
 * message 一律用错误码的英文 defaultMessage，不向客户端泄漏内部路径、堆栈或底层细节。
 * 与 common 的 {@code GlobalExceptionHandler} 并存：域内异常在此收敛，
 * 校验/兜底语义与 common 保持一致（重复注册时响应结构完全相同，对齐 educloud-file）。</p>
 */
@RestControllerAdvice
public final class CourseExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseExceptionHandler.class);

    private final ApiResponseFactory responses;

    public CourseExceptionHandler(ApiResponseFactory responses) {
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    /** BusinessException 直通（服务层抛出 CourseErrorCode），与 common GlobalExceptionHandler 语义一致。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return respond(exception.errorCode(), exception.getMessage(), exception.details());
    }

    /** 方法级安全/权限校验（@PreAuthorize 等）抛出的 Spring Security 拒绝 → 403 COURSE_ACCESS_DENIED。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return respond(
                CourseErrorCode.COURSE_ACCESS_DENIED,
                CourseErrorCode.COURSE_ACCESS_DENIED.defaultMessage(),
                null);
    }

    /** @Valid 请求体验证失败 → 400 VALIDATION_FAILED（复用 common 的 FieldViolation 结构）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(CourseExceptionHandler::toViolation)
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(violations));
    }

    /** 兜底：500 不泄漏细节，完整堆栈只进服务端日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        ApiResponse<Void> body = responses.error(
                CommonErrorCode.INTERNAL_ERROR,
                CommonErrorCode.INTERNAL_ERROR.defaultMessage(),
                null);
        LOGGER.error("Unhandled request failure requestId={}", body.requestId(), exception);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus())
                .header(RequestContext.REQUEST_ID_HEADER, body.requestId())
                .body(body);
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
