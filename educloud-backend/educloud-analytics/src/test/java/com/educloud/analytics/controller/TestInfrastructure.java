package com.educloud.analytics.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    @Bean
    public Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    public RequestIdPolicy requestIdPolicy() {
        return new RequestIdPolicy(UUID::randomUUID);
    }

    @Bean
    public RequestContextAccessor requestContextAccessor(RequestIdPolicy requestIdPolicy) {
        return new ServletRequestContextAccessor(requestIdPolicy, null);
    }

    @Bean
    public ApiResponseFactory apiResponseFactory(RequestContextAccessor requestContextAccessor, Clock clock) {
        return new ApiResponseFactory(requestContextAccessor, clock);
    }
}
