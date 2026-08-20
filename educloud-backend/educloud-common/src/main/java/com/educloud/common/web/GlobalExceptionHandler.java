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
        ApiResponse<T> body = responses.error(code, message, details);
        return responseEntity(code, body);
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
