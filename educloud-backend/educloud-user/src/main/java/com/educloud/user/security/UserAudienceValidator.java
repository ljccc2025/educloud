package com.educloud.user.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

/**
 * JWT audience 校验器（BUG-038）：Bearer 用户令牌必须携带包含预期 audience 的 aud 声明。
 *
 * <p>aud 缺失、aud 非数组（字符串/数字等非法形态被解析为 null）或 aud 与目标值不匹配一律拒绝，
 * 防止其它签发方/用途的令牌在 user 服务被当作用户令牌使用（令牌误用面收敛）。</p>
 *
 * <p>仅用于 resource-server 的 Bearer 用户令牌解码链路；internal 服务令牌（client_credentials）
 * 由 {@link InternalApiFilter} 走独立解码器与 aud=本服务 校验，不受本校验器影响。</p>
 */
public final class UserAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "The required audience is missing", null);

    private final String audience;

    public UserAudienceValidator(String audience) {
        this.audience = Objects.requireNonNull(audience, "audience");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
