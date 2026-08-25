package com.educloud.payment.security;

import com.educloud.payment.config.PaymentProperties;
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

public final class PaymentJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern AUTHORITY = Pattern.compile("[A-Za-z0-9:._*-]{1,128}");
    private static final Set<String> USER_TYPES = Set.of("STUDENT", "TEACHER", "ADMIN");
    private static final int MAX_AUTHORITIES = 64;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final String issuer;
    private final String audience;
    private final String internalAudience;
    private final Clock clock;

    public PaymentJwtValidator(PaymentProperties properties, Clock clock) {
        this.issuer = properties.jwt() != null ? properties.jwt().issuer() : "https://issuer.educloud.local";
        this.audience = properties.jwt() != null ? properties.jwt().audience() : "educloud-api";
        this.internalAudience = properties.internal() != null ? properties.internal().effectiveInternalAudience() : "educloud-payment";
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
        Instant issuedAt = instant(claims.get("iat"));
        Instant notBefore = instant(claims.get("nbf"));
        if (expiration == null || issuedAt == null || notBefore == null) {
            return false;
        }
        Instant now = clock.instant();
        if (expiration.isBefore(now.minus(CLOCK_SKEW))) {
            return false;
        }
        if (issuedAt.isAfter(now.plus(CLOCK_SKEW)) || notBefore.isAfter(now.plus(CLOCK_SKEW))) {
            return false;
        }
        return !expiration.isBefore(notBefore) && !expiration.isBefore(issuedAt);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return null;
    }

    private static boolean isIdentifier(Object value) {
        return value instanceof String text && IDENTIFIER.matcher(text).matches();
    }

    private static boolean isNonNegativeInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer >= 0;
        }
        if (value instanceof Long longValue) {
            return longValue >= 0;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.signum() >= 0;
        }
        if (value instanceof Short shortValue) {
            return shortValue >= 0;
        }
        if (value instanceof Byte byteValue) {
            return byteValue >= 0;
        }
        return false;
    }

    private static boolean isAuthorityCollection(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return false;
        }
        if (collection.size() > MAX_AUTHORITIES) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (Object element : collection) {
            if (!(element instanceof String text)
                    || !AUTHORITY.matcher(text).matches()
                    || !seen.add(text)) {
                return false;
            }
        }
        return true;
    }

    private static OAuth2TokenValidatorResult failure(String detail) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid " + detail, null));
    }
}
