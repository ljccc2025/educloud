package com.educloud.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "educloud.analytics")
public class AnalyticsProperties {

    private String environment = "prod";
    private JwtProperties jwt = new JwtProperties();
    private InternalProperties internal = new InternalProperties();

    @Data
    public static class JwtProperties {
        private String jwksLocation = "file:/tmp/educloud-live/jwks.json";
        private String issuer = "educloud-auth";
        private String audience = "educloud-web";
    }

    @Data
    public static class InternalProperties {
        private String audience = "educloud-analytics";
        private List<String> allowedClientIds = List.of(
                "gateway", "user-service", "educloud-order", "educloud-course",
                "educloud-content", "educloud-file", "educloud-payment",
                "educloud-live", "educloud-notification", "educloud-search"
        );
        private String secretToken = "educloud-internal-secret";
    }
}
