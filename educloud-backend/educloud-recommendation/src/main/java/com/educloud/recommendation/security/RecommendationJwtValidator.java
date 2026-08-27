package com.educloud.recommendation.security;

import com.educloud.recommendation.config.RecommendationProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * 推荐服务 JWT aud claim 校验器（复制 analytics AnalyticsJwtValidator 的 aud 校验段
 * 适配推荐模块；iss/时间戳校验由 {@link JwtDecoderConfiguration} 的
 * JwtIssuerValidator/JwtTimestampValidator 完成）。
 *
 * <p>User 令牌必须携带 aud 数组且包含 {@code educloud.recommendation.jwt.audience}
 * （默认 educloud-web）；缺失/非数组/不匹配均视为无效令牌（补上 aud 校验，消除
 * SecurityConfig 中「Gateway 已按 aud 过滤、服务内不校验」的死配置，与 course/
 * analytics 的 aud 契约对齐）。</p>
 */
public final class RecommendationJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final String DEFAULT_AUDIENCE = "educloud-web";

    private final String audience;

    public RecommendationJwtValidator(RecommendationProperties properties) {
        this.audience = properties != null && properties.getJwt() != null
                && properties.getJwt().getAudience() != null
                ? properties.getJwt().getAudience()
                : DEFAULT_AUDIENCE;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object audValue = token.getClaims().get("aud");
        if (!(audValue instanceof Collection<?> audiences)
                || !audiences.stream().allMatch(String.class::isInstance)
                || !audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Audience mismatch for educloud-recommendation", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
