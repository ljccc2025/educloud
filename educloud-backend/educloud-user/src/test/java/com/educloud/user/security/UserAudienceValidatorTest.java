package com.educloud.user.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserAudienceValidator 单元测试（BUG-038）：aud 含目标值通过；缺失/不匹配/非数组拒绝。
 */
class UserAudienceValidatorTest {

    private final UserAudienceValidator validator = new UserAudienceValidator("educloud-api");

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                claims);
    }

    @Test
    @DisplayName("aud 数组包含目标 audience 时校验通过")
    void acceptsWhenAudienceContainsTarget() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwt(Map.of("aud", List.of("educloud-api", "educloud-user"))));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("aud 缺失时拒绝")
    void rejectsWhenAudienceMissing() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(Map.of("sub", "1")));
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("aud 不含目标 audience 时拒绝")
    void rejectsWhenAudienceMismatch() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwt(Map.of("aud", List.of("educloud-user"))));
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("aud 非数组（数字等非法形态）时拒绝")
    void rejectsWhenAudienceIsNotArray() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(Map.of("aud", 12345)));
        assertThat(result.hasErrors()).isTrue();
    }
}
