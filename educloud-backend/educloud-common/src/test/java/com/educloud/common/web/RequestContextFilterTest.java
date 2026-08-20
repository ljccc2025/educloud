package com.educloud.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextFilterTest {

    private final RequestIdPolicy policy = new RequestIdPolicy(
            () -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private final RequestContextFilter filter = new RequestContextFilter(policy);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void exposesTheResolvedRequestIdAndCleansMdc() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "client.req-1");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            assertThat(MDC.get(RequestContext.MDC_KEY)).isEqualTo("client.req-1");
            assertThat(currentRequest.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE))
                    .isEqualTo("client.req-1");
        });

        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).isEqualTo("client.req-1");
        assertThat(MDC.get(RequestContext.MDC_KEY)).isNull();
    }

    @Test
    void restoresAnOuterMdcValueAfterAnException() {
        MDC.put(RequestContext.MDC_KEY, "outer-request");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new IOException("downstream failed");
        })).isInstanceOf(IOException.class);

        assertThat(MDC.get(RequestContext.MDC_KEY)).isEqualTo("outer-request");
        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER))
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    }
}
