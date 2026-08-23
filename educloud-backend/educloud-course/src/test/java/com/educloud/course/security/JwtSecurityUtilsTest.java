package com.educloud.course.security;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JwtSecurityUtils 单元测试：subject=userId 解析与 permissions 提取。 */
class JwtSecurityUtilsTest {

    @Test
    void parsesUserIdAndPermissionsFromJwt() {
        Jwt jwt = jwt("1001", "session-1001", List.of("course:create", "course:audit"), List.of("TEACHER"));

        assertThat(JwtSecurityUtils.userId(jwt)).isEqualTo(1001L);
        assertThat(JwtSecurityUtils.permissions(jwt))
                .containsExactlyInAnyOrder("course:create", "course:audit");
        assertThat(JwtSecurityUtils.hasPermission(jwt, "course:audit")).isTrue();
        assertThat(JwtSecurityUtils.hasPermission(jwt, "course:enroll")).isFalse();

        AuthenticatedUser user = JwtSecurityUtils.authenticatedUser(jwt);
        assertThat(user.userId()).isEqualTo("1001");
        assertThat(user.sessionId()).isEqualTo("session-1001");
        assertThat(user.roles()).containsExactly("TEACHER");
        assertThat(user.permissions()).containsExactlyInAnyOrder("course:create", "course:audit");
    }

    @Test
    void treatsMissingPermissionsAsEmpty() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("1001")
                .claim("sid", "session-1001")
                .build();

        assertThat(JwtSecurityUtils.permissions(jwt)).isEmpty();
        assertThat(JwtSecurityUtils.hasPermission(jwt, "course:audit")).isFalse();
    }

    @Test
    void rejectsNonNumericSubjectAndMalformedPermissions() {
        Jwt nonNumeric = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("not-a-number")
                .claim("sid", "s")
                .claim("permissions", List.of("course:audit"))
                .build();
        assertThatThrownBy(() -> JwtSecurityUtils.userId(nonNumeric))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("numeric userId");

        Jwt malformed = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("1001")
                .claim("sid", "s")
                .claim("permissions", "course:audit")
                .build();
        assertThatThrownBy(() -> JwtSecurityUtils.permissions(malformed))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("array of strings");
    }

    private static Jwt jwt(String subject, String sessionId, List<String> permissions, List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://issuer.educloud.local")
                .subject(subject)
                .audience(List.of("educloud-api"))
                .issuedAt(now.minusSeconds(1))
                .notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300))
                .claim("sid", sessionId)
                .claim("tokenVersion", 1L)
                .claim("userType", "STUDENT")
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
    }
}