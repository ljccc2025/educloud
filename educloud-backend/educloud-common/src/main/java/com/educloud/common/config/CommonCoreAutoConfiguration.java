package com.educloud.common.config;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CommonProperties.class)
public class CommonCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock commonClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    JavaTimeModule commonJavaTimeModule() {
        return new JavaTimeModule();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiResponseFactory commonApiResponseFactory(
            ObjectProvider<RequestContextAccessor> requestContextAccessor,
            Clock clock) {
        return new ApiResponseFactory(
                requestContextAccessor.getIfAvailable(FallbackRequestContextAccessor::new),
                clock);
    }

    private static final class FallbackRequestContextAccessor implements RequestContextAccessor {

        @Override
        public String requestId() {
            return UUID.randomUUID().toString();
        }

        @Override
        public Optional<String> traceId() {
            return Optional.empty();
        }
    }
}
