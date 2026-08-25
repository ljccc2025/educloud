package com.educloud.payment.exception;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.web.RequestContextAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaymentExceptionHandlerTest {

    private final RequestContextAccessor requestContext = new RequestContextAccessor() {
        @Override
        public String requestId() {
            return "req-test-123";
        }

        @Override
        public Optional<String> traceId() {
            return Optional.of("trace-test-123");
        }
    };
    private final ApiResponseFactory responseFactory = new ApiResponseFactory(
            requestContext,
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
    );
    private final PaymentExceptionHandler handler = new PaymentExceptionHandler(responseFactory);

    @Test
    void handleBusinessException_returnsCorrectStatusAndEnvelope() {
        PaymentBizException exception = new PaymentBizException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单 123 未找到");
        ResponseEntity<ApiResponse<Object>> response = handler.handleBusinessException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PAYMENT_ORDER_NOT_FOUND", response.getBody().code());
        assertEquals("支付单 123 未找到", response.getBody().message());
        assertEquals("req-test-123", response.getBody().requestId());
    }

    @Test
    void handleAccessDenied_returnsForbidden() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(new org.springframework.security.access.AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(CommonErrorCode.ACCESS_DENIED.code(), response.getBody().code());
    }
}
