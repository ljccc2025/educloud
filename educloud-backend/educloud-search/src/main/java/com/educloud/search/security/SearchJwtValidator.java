package com.educloud.search.security;

import com.educloud.search.config.SearchProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Search 服务 JWT 校验器
 * 校验 Issuer、Audience 以及 Token 有效时间窗口。
 */
public final class SearchJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final String issuer;
    private final String audience;
    private final String internalAudience;
    private final Clock clock;

    public SearchJwtValidator(SearchProperties properties, Clock clock) {
        this.issuer = properties != null && properties.getJwt() != null && properties.getJwt().getIssuer() != null
                ? properties.getJwt().getIssuer()
                : "educloud-auth";
        this.audience = properties != null && properties.getJwt() != null && properties.getJwt().getAudience() != null
                ? properties.getJwt().getAudience()
                : "educloud-web";
        this.internalAudience = properties != null && properties.getInternal() != null && properties.getInternal().getAudience() != null
                ? properties.getInternal().getAudience()
                : "educloud-search";
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Map<String, Object> claims = token.getClaims();

        // 1. 校验 Issuer
        Object iss = claims.get("iss");
        if (!issuer.equals(iss) && !"https://issuer.educloud.local".equals(iss) && !"educloud-auth".equals(iss)) {
            return failure("issuer", "Invalid token issuer: " + iss);
        }

        // 2. 校验 Audience
        Object audValue = claims.get("aud");
        if (!(audValue instanceof Collection<?> audiences)
                || !audiences.stream().allMatch(String.class::isInstance)) {
            return failure("audience", "Invalid token audience structure");
        }

        boolean matchAudience = audiences.contains(audience)
                || audiences.contains("educloud-web")
                || audiences.contains("educloud-api")
                || audiences.contains(internalAudience);

        if (!matchAudience) {
            return failure("audience", "Audience mismatch for educloud-search");
        }

        // 3. 校验时间窗口
        if (!hasValidTimestamps(claims)) {
            return failure("expired", "Token is expired or not yet valid");
        }

        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasValidTimestamps(Map<String, Object> claims) {
        Instant expiration = instant(claims.get("exp"));
        Instant notBefore = instant(claims.get("nbf"));
        Instant issuedAt = instant(claims.get("iat"));
        Instant now = clock.instant();

        if (expiration != null && expiration.isBefore(now.minus(CLOCK_SKEW))) {
            return false;
        }
        if (notBefore != null && notBefore.isAfter(now.plus(CLOCK_SKEW))) {
            return false;
        }
        if (issuedAt != null && issuedAt.isAfter(now.plus(CLOCK_SKEW))) {
            return false;
        }
        return true;
    }

    private static Instant instant(Object value) {
        return value instanceof Instant instant ? instant : null;
    }

    private static OAuth2TokenValidatorResult failure(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }
}
