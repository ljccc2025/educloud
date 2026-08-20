package com.educloud.common.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ServletRequestContextAccessor implements RequestContextAccessor {

    private final RequestIdPolicy requestIdPolicy;
    private final Tracer tracer;

    public ServletRequestContextAccessor(RequestIdPolicy requestIdPolicy, Tracer tracer) {
        this.requestIdPolicy = Objects.requireNonNull(requestIdPolicy, "requestIdPolicy");
        this.tracer = tracer;
    }

    @Override
    public String requestId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            Object value = servletAttributes.getRequest().getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
            if (value instanceof String requestId && !requestId.isBlank()) {
                return requestId;
            }
        }
        return requestIdPolicy.resolve(null);
    }

    @Override
    public Optional<String> traceId() {
        if (tracer == null) {
            return Optional.empty();
        }
        Span span = tracer.currentSpan();
        if (span == null || span.context() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(span.context().traceId()).filter(value -> !value.isBlank());
    }
}
