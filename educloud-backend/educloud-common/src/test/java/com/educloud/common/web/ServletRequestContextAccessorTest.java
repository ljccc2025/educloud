package com.educloud.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ServletRequestContextAccessorTest {

    private final RequestIdPolicy policy = new RequestIdPolicy(
            () -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsRequestIdFromTheCurrentServletRequest() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, "req-9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var accessor = new ServletRequestContextAccessor(policy, null);

        assertThat(accessor.requestId()).isEqualTo("req-9");
        assertThat(accessor.traceId()).isEmpty();
    }

    @Test
    void readsOnlyARealMicrometerTraceId() {
        var tracer = mock(Tracer.class);
        var span = mock(Span.class);
        var traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("abcdef0123456789");

        var accessor = new ServletRequestContextAccessor(policy, tracer);

        assertThat(accessor.traceId()).contains("abcdef0123456789");
    }

    @Test
    void generatesARequestIdOutsideAServletRequestWithoutFakingATrace() {
        var accessor = new ServletRequestContextAccessor(policy, null);

        assertThat(accessor.requestId()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(accessor.traceId()).isEmpty();
    }
}
