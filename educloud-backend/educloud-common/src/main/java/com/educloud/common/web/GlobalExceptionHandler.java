package com.educloud.common.web;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.FieldViolation;
import com.educloud.common.error.ValidationErrorDetails;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.context.MessageSourceResolvable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiResponseFactory responses;

    public GlobalExceptionHandler(ApiResponseFactory responses) {
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .sorted(violationComparator())
                .toList();
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(violations));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleMethodValidation(
            HandlerMethodValidationException exception) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : exception.getAllValidationResults()) {
            String parameterName = result.getMethodParameter().getParameterName();
            String field = parameterName == null || parameterName.isBlank() ? "argument" : parameterName;
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String code = firstCode(error);
                String message = error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage();
                violations.add(new FieldViolation(field, code, message));
            }
        }
        violations.sort(violationComparator());
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleUnreadableJson(
            HttpMessageNotReadableException exception) {
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(List.of()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return respond(exception.errorCode(), exception.getMessage(), exception.details());
    }

    /**
     * Spring MVC 客户端错误（缺参/类型不匹配/缺头/方法不允许/媒体类型不支持/资源不存在）：
     * 此前全部落入 Exception 兜底返回 500，客户端无法区分参数错误与服务器故障。
     */
    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class })
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleSpringMvcClientErrors(
            Exception exception) {
        if (exception instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {
            return respond(CommonErrorCode.VALIDATION_FAILED, "Method not allowed",
                    new ValidationErrorDetails(List.of()), 405);
        }
        if (exception instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            return respond(CommonErrorCode.VALIDATION_FAILED, "Resource not found",
                    new ValidationErrorDetails(List.of()), 404);
        }
        return respond(CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        ApiResponse<Void> body = responses.error(
                CommonErrorCode.INTERNAL_ERROR,
                CommonErrorCode.INTERNAL_ERROR.defaultMessage(),
                null);
        LOGGER.error("Unhandled request failure requestId={}", body.requestId(), exception);
        return responseEntity(CommonErrorCode.INTERNAL_ERROR, body);
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(ErrorCode code, String message, T details) {
        return respond(code, message, details, code.httpStatus());
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(
            ErrorCode code, String message, T details, int status) {
        ApiResponse<T> body = responses.error(code, message, details);
        return ResponseEntity.status(status)
                .header(RequestContext.REQUEST_ID_HEADER, body.requestId())
                .body(body);
    }

    private static <T> ResponseEntity<ApiResponse<T>> responseEntity(
            ErrorCode code,
            ApiResponse<T> body) {
        return ResponseEntity.status(code.httpStatus())
                .header(RequestContext.REQUEST_ID_HEADER, body.requestId())
                .body(body);
    }

    private static FieldViolation toViolation(FieldError error) {
        String code = error.getCode() == null ? "Invalid" : error.getCode();
        String message = error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage();
        return new FieldViolation(error.getField(), code, message);
    }

    private static String firstCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        if (codes == null || codes.length == 0 || codes[0] == null || codes[0].isBlank()) {
            return "Invalid";
        }
        return codes[0];
    }

    private static Comparator<FieldViolation> violationComparator() {
        return Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code);
    }
}
