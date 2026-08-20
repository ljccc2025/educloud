package com.educloud.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.educloud.common.web.RequestContextAccessor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiResponseFactoryTest {

    @Test
    void createsASuccessResponseFromTheCurrentRequestAndClock() {
        RequestContextAccessor requestContext = new RequestContextAccessor() {
            @Override
            public String requestId() {
                return "req-1";
            }

            @Override
            public Optional<String> traceId() {
                return Optional.of("trace-1");
            }
        };
        var instant = Instant.parse("2026-08-20T08:00:00Z");
        var factory = new ApiResponseFactory(requestContext, Clock.fixed(instant, ZoneOffset.UTC));

        var response = factory.success("payload");

        assertThat(response).isEqualTo(new ApiResponse<>(
                "SUCCESS", "OK", "payload", "req-1", instant));
    }
}
