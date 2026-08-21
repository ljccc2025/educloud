package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayWebProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class CorsConfiguration {

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "X-Request-Id",
            "Idempotency-Key",
            "If-Match",
            "Accept-Language");
    private static final List<String> EXPOSED_HEADERS = List.of(
            "X-Request-Id", "Retry-After");

    @Bean
    @Order(GatewayFilterOrders.CORS)
    public CorsWebFilter gatewayCorsWebFilter(GatewayWebProperties properties) {
        org.springframework.web.cors.CorsConfiguration configuration =
                new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(properties.getAllowedOrigins()));
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new OrderedCorsWebFilter(source);
    }

    private static final class OrderedCorsWebFilter extends CorsWebFilter implements Ordered {

        private OrderedCorsWebFilter(UrlBasedCorsConfigurationSource source) {
            super(source);
        }

        @Override
        public int getOrder() {
            return GatewayFilterOrders.CORS;
        }
    }
}
