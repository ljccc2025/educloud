package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewaySecurityProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class GatewayJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern AUTHORITY = Pattern.compile("[A-Za-z0-9:._*-]{1,128}");
    private static final Set<String> USER_TYPES = Set.of("STUDENT", "TEACHER", "ADMIN");
    private static final int MAX_AUTHORITIES = 64;

    private final String issuer;
    private final String audience;
    private final Duration clockSkew;
    private final Clock clock;

    public GatewayJwtValidator(GatewaySecurityProperties properties, Clock clock) {
        this.issuer = properties.getIssuer();
        this.audience = properties.getAudience();
        this.clockSkew = properties.getClockSkew();
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Map<String, Object> claims = token.getClaims();
        if (!issuer.equals(claims.get("iss"))) {
            return failure("issuer");
        }
        if (!hasAudience(claims.get("aud"))) {
            return failure("audience");
        }
        if (!hasValidTimestamps(claims)) {
            return failure("timestamp");
        }
        if (!isIdentifier(claims.get("sub"))
                || !isIdentifier(claims.get("sid"))
                || !isNonNegativeInteger(claims.get("tokenVersion"))
                || !(claims.get("userType") instanceof String userType)
                || !USER_TYPES.contains(userType)
                || !isAuthorityCollection(claims.get("roles"))
                || !isAuthorityCollection(claims.get("permissions"))) {
            return failure("claims");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasAudience(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return false;
        }
        return values.stream().allMatch(String.class::isInstance) && values.contains(audience);
    }

    private boolean hasValidTimestamps(Map<String, Object> claims) {
        Instant expiration = instant(claims.get("exp"));
        Instant notBefore = instant(claims.get("nbf"));
        Instant issuedAt = instant(claims.get("iat"));
        if (expiration == null || notBefore == null || issuedAt == null) {
            return false;
        }
        Instant now = clock.instant();
        return !expiration.isBefore(now.minus(clockSkew))
                && !notBefore.isAfter(now.plus(clockSkew))
                && !issuedAt.isAfter(now.plus(clockSkew));
    }

    private static Instant instant(Object value) {
        return value instanceof Instant instant ? instant : null;
    }

    private static boolean isIdentifier(Object value) {
        return value instanceof String text && IDENTIFIER.matcher(text).matches();
    }

    private static boolean isNonNegativeInteger(Object value) {
        if (value instanceof Byte number) {
            return number >= 0;
        }
        if (value instanceof Short number) {
            return number >= 0;
        }
        if (value instanceof Integer number) {
            return number >= 0;
        }
        if (value instanceof Long number) {
            return number >= 0;
        }
        return value instanceof BigInteger number && number.signum() >= 0;
    }

    private static boolean isAuthorityCollection(Object value) {
        if (value == null) {
            return true;
        }
        if (!(value instanceof Collection<?> authorities) || authorities.size() > MAX_AUTHORITIES) {
            return false;
        }
        Set<String> unique = new HashSet<>();
        for (Object authority : authorities) {
            if (!(authority instanceof String text)
                    || !AUTHORITY.matcher(text).matches()
                    || !unique.add(text)) {
                return false;
            }
        }
        return true;
    }

    private static OAuth2TokenValidatorResult failure(String category) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", category, null));
    }
}
