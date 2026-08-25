package com.educloud.live.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "educloud.live")
public class LiveProperties {

    private String environment = "prod";
    private JwtProperties jwt = new JwtProperties();
    private InternalProperties internal = new InternalProperties();
    private StreamProperties stream = new StreamProperties();

    @Data
    public static class JwtProperties {
        private String jwksLocation;
        private String issuer;
        private String audience;
    }

    @Data
    public static class InternalProperties {
        private String audience;
        private List<String> allowedClientIds;
        private String secretToken;
    }

    @Data
    public static class StreamProperties {
        private String providerType = "MOCK";
        private MockStreamProperties mock = new MockStreamProperties();
    }

    @Data
    public static class MockStreamProperties {
        private String pushBaseUrl = "rtmp://live-mock.educloud.cn/live";
        private String playFlvBaseUrl = "http://live-mock.educloud.cn/live";
        private String playHlsBaseUrl = "http://live-mock.educloud.cn/live";
        private String playWebrtcBaseUrl = "webrtc://live-mock.educloud.cn/live";
        private String secretKey = "educloud-live-secret-key-2026";
        private long tokenExpireSeconds = 86400;
    }
}
