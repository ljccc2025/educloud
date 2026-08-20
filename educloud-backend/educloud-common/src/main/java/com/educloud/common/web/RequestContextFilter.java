package com.educloud.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestContextFilter extends OncePerRequestFilter {

    private final RequestIdPolicy requestIdPolicy;

    public RequestContextFilter(RequestIdPolicy requestIdPolicy) {
        this.requestIdPolicy = Objects.requireNonNull(requestIdPolicy, "requestIdPolicy");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestIdPolicy.resolve(request.getHeader(RequestContext.REQUEST_ID_HEADER));
        String previousRequestId = MDC.get(RequestContext.MDC_KEY);

        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        MDC.put(RequestContext.MDC_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousRequestId == null) {
                MDC.remove(RequestContext.MDC_KEY);
            } else {
                MDC.put(RequestContext.MDC_KEY, previousRequestId);
            }
        }
    }
}
