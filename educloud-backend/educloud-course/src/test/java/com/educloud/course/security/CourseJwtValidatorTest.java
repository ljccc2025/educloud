package com.educloud.course.security;

import com.educloud.course.config.CourseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** CourseJwtValidator 单元测试：服务令牌宽松分支与双 aud 严格优先。 */
class CourseJwtValidatorTest {

    private static final String ISSUER = "https://issuer.educloud.local";
    private static final Instant NOW = Instant.parse("2026-08-23T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final CourseJwtValidator validator = new CourseJwtValidator(properties(), CLOCK);

    private static CourseProperties properties() {
        return new CourseProperties(
                "test",
                new CourseProperties.Jwt("", ISSUER, "educloud-api"),
                new CourseProperties.Internal(List.of("educloud-content"), "educloud-course"));
    }

    @Test
    void acceptsServiceTokenViaLenientBranch() {
        // 服务令牌：仅 iss/aud/时间字段 + clientId，无用户 claims 契约 → 宽松分支放行。
        Jwt service = jwt("service:educloud-content", List.of("educloud-course"),
                Map.of("clientId", "educloud-content"));

        assertThat(validator.validate(service).hasErrors()).isFalse();
    }

    @Test
    void dualAudiencePrefersStrictUserContract() {
        // aud 同时含 educloud-api 与 educloud-course：必须满足严格用户契约（fail-closed）。
        Jwt missingClaims = jwt("1001", List.of("educloud-api", "educloud-course"), Map.of());
        assertThat(validator.validate(missingClaims).hasErrors()).isTrue();

        Jwt valid = jwt("1001", List.of("educloud-api", "educloud-course"), userClaims("session-1001"));
        assertThat(validator.validate(valid).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongAudienceAndMissingAudience() {
        Jwt wrongAudience = jwt("1001", List.of("educloud-user"), userClaims("session-1001"));
        assertThat(validator.validate(wrongAudience).hasErrors()).isTrue();

        Jwt missingAudience = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .issuedAt(NOW.minusSeconds(1))
                .notBefore(NOW.minusSeconds(1))
                .expiresAt(NOW.plusSeconds(300))
                .subject("1001")
                .build();
        assertThat(validator.validate(missingAudience).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongIssuer() {
        Jwt wrongIssuer = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("https://evil.example")
                .subject("1001")
                .audience(List.of("educloud-api"))
                .issuedAt(NOW.minusSeconds(1))
                .notBefore(NOW.minusSeconds(1))
                .expiresAt(NOW.plusSeconds(300))
                .build();
        assertThat(validator.validate(wrongIssuer).hasErrors()).isTrue();
    }

    @Test
    void rejectsStrictClaimViolations() {
        // permissions 非集合 → 严格分支 claims 失败
        Map<String, Object> claims = userClaims("session-1001");
        claims.put("permissions", "course:audit");
        Jwt malformedPermissions = jwt("1001", List.of("educloud-api"), claims);
        assertThat(validator.validate(malformedPermissions).hasErrors()).isTrue();
    }

    private static Jwt jwt(String subject, List<String> audiences, Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(subject)
                .audience(audiences)
                .issuedAt(NOW.minusSeconds(1))
                .notBefore(NOW.minusSeconds(1))
                .expiresAt(NOW.plusSeconds(300));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }

    private static Map<String, Object> userClaims(String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sid", sessionId);
        claims.put("tokenVersion", 1L);
        claims.put("userType", "STUDENT");
        claims.put("roles", List.of("STUDENT"));
        claims.put("permissions", List.of("course:audit"));
        return claims;
    }
}
