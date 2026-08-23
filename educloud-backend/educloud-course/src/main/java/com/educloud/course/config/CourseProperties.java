package com.educloud.course.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * `educloud.course` 全部配置（任务 6 起：jwt 验签段与 internal 内部接口段；任务 12 的
 * file 段由 CourseFileProperties 单独承载，不在此重复绑定）。依据：M05 设计规格第 10 节。
 */
@Validated
@ConfigurationProperties("educloud.course")
public record CourseProperties(
        String environment,
        Jwt jwt,
        Internal internal) {

    /** environment 默认 local：与 user/file SessionProperties 同源（同一 EDUCLOUD_ENVIRONMENT）。 */
    public CourseProperties {
        if (environment == null || environment.isBlank()) {
            environment = "local";
        }
    }

    /** 用户令牌验签（Resource Server）：User 公钥 JWKS 位置、issuer/audience（规格 9 节）。 */
    public record Jwt(
            String jwksLocation,
            String issuer,
            String audience) {
    }

    /** 内部 API 服务令牌：clientId 白名单与期望 audience（复刻 user/file InternalApiFilter 模式）。 */
    public record Internal(
            List<String> allowedClientIds,
            String audience) {

        /** 白名单缺省为空（fail-closed）；未配置时不放行任何内部调用方。 */
        public List<String> effectiveAllowedClientIds() {
            return allowedClientIds == null ? List.of() : List.copyOf(allowedClientIds);
        }

        /** 内部接口期望的 audience；未配置时默认 educloud-course。 */
        public String effectiveInternalAudience() {
            return audience == null || audience.isBlank() ? "educloud-course" : audience;
        }
    }
}