package com.educloud.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "educloud.notification")
public class NotificationProperties {

    private String environment = "prod";
    private JwtProperties jwt = new JwtProperties();
    private InternalProperties internal = new InternalProperties();
    private EmailProperties email = new EmailProperties();

    @Data
    public static class JwtProperties {
        private String jwksLocation = "file:/tmp/educloud-live/jwks.json";
        private String issuer = "https://issuer.educloud.local";
        private String audience = "educloud-api";
    }

    @Data
    public static class InternalProperties {
        private String audience = "educloud-notification";
        private String allowedClientIds = "gateway,user-service,educloud-order,educloud-course,educloud-content,educloud-file,educloud-payment,educloud-live";
        private String secretToken = "educloud-internal-secret";
    }

    @Data
    public static class EmailProperties {
        private String provider = "mock";
        private String host = "smtp.educloud.local";
        private Integer port = 465;
        private String username = "support@educloud.cn";
        private String password = "secret_smtp_token";
        private String from = "EduCloud Notifications <support@educloud.cn>";
        private boolean sslEnabled = true;
    }
}
