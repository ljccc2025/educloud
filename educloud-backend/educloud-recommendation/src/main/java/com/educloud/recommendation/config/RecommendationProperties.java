package com.educloud.recommendation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * `educloud.recommendation` 配置（M13 任务 7）。
 *
 * <p>jwt 段与 course/file 同构：User 公钥 JWKS 验签所需位置与 issuer/audience；
 * cacheTtlSeconds 供规则配置本地缓存使用（RuleConfigService 60s）。默认值与
 * application.yml 对齐（file:/tmp/educloud-live/jwks.json / educloud-auth /
 * educloud-web），可由 env 覆盖（RECOMMENDATION_JWKS_LOCATION 等）。</p>
 */
@Data
@ConfigurationProperties(prefix = "educloud.recommendation")
public class RecommendationProperties {

    private Jwt jwt = new Jwt();
    private long cacheTtlSeconds = 60;

    /** 用户令牌验签（Resource Server）：User 公钥 JWKS 位置、issuer/audience。 */
    @Data
    public static class Jwt {

        private String jwksLocation = "file:/tmp/educloud-live/jwks.json";
        private String issuer = "educloud-auth";
        private String audience = "educloud-web";
    }
}
