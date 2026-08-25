package com.educloud.payment.exception;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.FieldViolation;
import com.educloud.common.error.ValidationErrorDetails;
import com.educloud.common.web.RequestContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public final class PaymentExceptionHandler {

    private final ApiResponseFactory responses;

    public PaymentExceptionHandler(ApiResponseFactory responses) {
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return respond(exception.errorCode(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return respond(
                CommonErrorCode.ACCESS_DENIED,
                CommonErrorCode.ACCESS_DENIED.defaultMessage(),
                null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(PaymentExceptionHandler::toViolation)
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
