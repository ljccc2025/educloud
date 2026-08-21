package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayJwtValidatorTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void acceptsTheCompleteClaimContract() {
        assertThat(validate(validClaims(), Duration.ofSeconds(30)).hasErrors()).isFalse();
    }

    @Test
    void rejectsIssuerAndAudienceMismatchWithStableCategories() {
        assertCategory(with("iss", "https://other.example"), "issuer");
        assertCategory(with("aud", List.of("another-api")), "audience");
        assertCategory(with("aud", "educloud-api"), "audience");
    }

    @Test
    void requiresTypedAndCurrentTimeClaims() {
        for (String claim : List.of("exp", "nbf", "iat")) {
            Map<String, Object> missing = validClaims();
            missing.remove(claim);
            assertCategory(missing, "timestamp");

            assertCategory(with(claim, "not-a-time"), "timestamp");
        }

        assertCategory(with("exp", NOW.minusSeconds(31)), "timestamp");
        assertCategory(with("nbf", NOW.plusSeconds(31)), "timestamp");
        assertCategory(with("iat", NOW.plusSeconds(31)), "timestamp");
    }

    @Test
    void honorsClockSkewBoundariesAtZeroThirtyAndOneHundredTwentySeconds() {
        assertThat(validate(with("exp", NOW), Duration.ZERO).hasErrors()).isFalse();
        assertThat(validate(with("nbf", NOW), Duration.ZERO).hasErrors()).isFalse();
        assertThat(validate(with("iat", NOW), Duration.ZERO).hasErrors()).isFalse();

        assertThat(validate(with("exp", NOW.minusSeconds(30)), Duration.ofSeconds(30)).hasErrors()).isFalse();
        assertThat(validate(with("nbf", NOW.plusSeconds(30)), Duration.ofSeconds(30)).hasErrors()).isFalse();
        assertThat(validate(with("iat", NOW.plusSeconds(30)), Duration.ofSeconds(30)).hasErrors()).isFalse();

        assertThat(validate(with("exp", NOW.minusSeconds(120)), Duration.ofSeconds(120)).hasErrors()).isFalse();
        assertThat(validate(with("nbf", NOW.plusSeconds(120)), Duration.ofSeconds(120)).hasErrors()).isFalse();
        assertThat(validate(with("iat", NOW.plusSeconds(120)), Duration.ofSeconds(120)).hasErrors()).isFalse();
    }

    @Test
    void enforcesSafeSubjectAndSessionIdentifiers() {
        for (String claim : List.of("sub", "sid")) {
            Map<String, Object> missing = validClaims();
            missing.remove(claim);
            assertCategory(missing, "claims");
            assertCategory(with(claim, ""), "claims");
            assertCategory(with(claim, "x".repeat(129)), "claims");
            assertCategory(with(claim, "unsafe\nvalue"), "claims");
            assertCategory(with(claim, "slash/value"), "claims");
        }
    }

    @Test
    void enforcesTokenVersionAndUserType() {
        Map<String, Object> missingVersion = validClaims();
        missingVersion.remove("tokenVersion");
        assertCategory(missingVersion, "claims");
        assertCategory(with("tokenVersion", "1"), "claims");
        assertCategory(with("tokenVersion", 1.0d), "claims");
        assertCategory(with("tokenVersion", -1), "claims");

        Map<String, Object> missingUserType = validClaims();
        missingUserType.remove("userType");
        assertCategory(missingUserType, "claims");
        assertCategory(with("userType", "OWNER"), "claims");
        for (String allowed : List.of("STUDENT", "TEACHER", "ADMIN")) {
            assertThat(validate(with("userType", allowed), Duration.ofSeconds(30)).hasErrors()).isFalse();
        }
    }

    @Test
    void boundsOptionalRolesAndPermissionsCollections() {
        assertCategory(with("roles", "ADMIN"), "claims");
        assertCategory(with("permissions", "course:read"), "claims");
        assertCategory(with("roles", List.of("")), "claims");
        assertCategory(with("permissions", List.of("unsafe/value")), "claims");
        assertCategory(with("roles", List.of("x".repeat(129))), "claims");
        assertCategory(with("roles", List.of("ADMIN", "ADMIN")), "claims");

        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            tooMany.add("role-" + index);
        }
        assertCategory(with("roles", tooMany), "claims");

        Map<String, Object> withoutCollections = validClaims();
        withoutCollections.remove("roles");
        withoutCollections.remove("permissions");
        assertThat(validate(withoutCollections, Duration.ofSeconds(30)).hasErrors()).isFalse();
    }

    private static OAuth2TokenValidatorResult validate(Map<String, Object> claims, Duration skew) {
        return new GatewayJwtValidator(properties(skew), CLOCK).validate(jwt(claims));
    }

    private static void assertCategory(Map<String, Object> claims, String category) {
        OAuth2TokenValidatorResult result = validate(claims, Duration.ofSeconds(30));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).extracting(error -> error.getDescription()).contains(category);
    }

    private static GatewaySecurityProperties properties(Duration skew) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setJwksJson("{\"keys\":[]}");
        properties.setIssuer("https://identity.educloud.local");
        properties.setAudience("educloud-api");
        properties.setClockSkew(skew);
        return properties;
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaims()).thenReturn(claims);
        return jwt;
    }

    private static Map<String, Object> with(String name, Object value) {
        Map<String, Object> claims = validClaims();
        claims.put(name, value);
        return claims;
    }

    private static Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://identity.educloud.local");
        claims.put("aud", List.of("educloud-api", "educloud-web"));
        claims.put("exp", NOW.plusSeconds(300));
        claims.put("nbf", NOW.minusSeconds(1));
        claims.put("iat", NOW.minusSeconds(1));
        claims.put("sub", "user:1001");
        claims.put("sid", "session-1001");
        claims.put("tokenVersion", 1L);
        claims.put("userType", "STUDENT");
        claims.put("roles", List.of("STUDENT"));
        claims.put("permissions", List.of("course:read", "assignment:*"));
        return claims;
    }
}
