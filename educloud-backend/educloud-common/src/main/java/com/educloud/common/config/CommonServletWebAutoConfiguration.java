package com.educloud.common.config;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.GlobalExceptionHandler;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = CommonCoreAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(name = {
    "jakarta.servlet.Servlet",
    "org.springframework.web.servlet.DispatcherServlet"
})
public class CommonServletWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RequestIdPolicy commonRequestIdPolicy() {
        return new RequestIdPolicy(UUID::randomUUID);
    }

    @Bean
    @ConditionalOnMissingBean(RequestContextAccessor.class)
    ServletRequestContextAccessor commonRequestContextAccessor(
            RequestIdPolicy requestIdPolicy,
            ObjectProvider<Tracer> tracer) {
        return new ServletRequestContextAccessor(requestIdPolicy, tracer.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    RequestContextFilter commonRequestContextFilter(RequestIdPolicy requestIdPolicy) {
        return new RequestContextFilter(requestIdPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler commonGlobalExceptionHandler(ApiResponseFactory responses) {
        return new GlobalExceptionHandler(responses);
    }
}
