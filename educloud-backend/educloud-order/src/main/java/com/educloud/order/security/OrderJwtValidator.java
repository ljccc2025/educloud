package com.educloud.order.security;

import com.educloud.order.config.OrderProperties;
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

public final class OrderJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern AUTHORITY = Pattern.compile("[A-Za-z0-9:._*-]{1,128}");
    private static final Set<String> USER_TYPES = Set.of("STUDENT", "TEACHER", "ADMIN");
    private static final int MAX_AUTHORITIES = 64;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final String issuer;
    private final String audience;
    private final String internalAudience;
    private final Clock clock;

    public OrderJwtValidator(OrderProperties properties, Clock clock) {
        this.issuer = properties.jwt() != null ? properties.jwt().issuer() : "https://issuer.educloud.local";
        this.audience = properties.jwt() != null ? properties.jwt().audience() : "educloud-api";
        this.internalAudience = properties.internal() != null ? properties.internal().effectiveInternalAudience() : "educloud-order";
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Map<String, Object> claims = token.getClaims();
        if (!issuer.equals(claims.get("iss"))) {
            return failure("issuer");
        }
        Object audValue = claims.get("aud");
        if (!(audValue instanceof Collection<?> audiences)
                || !audiences.stream().allMatch(String.class::isInstance)) {
            return failure("audience");
        }
        if (audiences.contains(audience)) {
            if (!hasValidTimestamps(claims)
                    || !isIdentifier(claims.get("sub"))
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
        if (audiences.contains(internalAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return failure("audience");
    }

    private boolean hasValidTimestamps(Map<String, Object> claims) {
        Instant expiration = instant(claims.get("exp"));
        Instant notBefore = instant(claims.get("nbf"));
        Instant issuedAt = instant(claims.get("iat"));
        if (expiration == null || notBefore == null || issuedAt == null) {
            return false;
        }
        Instant now = clock.instant();
        return !expiration.isBefore(now.minus(CLOCK_SKEW))
                && !notBefore.isAfter(now.plus(CLOCK_SKEW))
                && !issuedAt.isAfter(now.plus(CLOCK_SKEW));
    }

    private static Instant instant(Object value) {
        return value instanceof Instant instant ? instant : null;
    }

    private static boolean isIdentifier(Object value) {
        return value instanceof String text && IDENTIFIER.matcher(text).matches();
    }

    private static boolean isNonNegativeInteger(Object value) {
        if (value instanceof Number number) {
            return number.longValue() >= 0;
        }
        if (value instanceof String text) {
            try {
                return new BigInteger(text).signum() >= 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isAuthorityCollection(Object value) {
        if (!(value instanceof Collection<?> collection) || collection.size() > MAX_AUTHORITIES) {
            return false;
        }
        Set<String> unique = new HashSet<>();
        for (Object item : collection) {
            if (!(item instanceof String text)
                    || !AUTHORITY.matcher(text).matches()
                    || !unique.add(text)) {
                return false;
            }
        }
        return true;
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
