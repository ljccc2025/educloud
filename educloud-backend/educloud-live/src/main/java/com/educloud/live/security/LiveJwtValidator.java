package com.educloud.live.security;

import com.educloud.live.config.LiveProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;

public final class LiveJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final String issuer;
    private final String audience;
    private final Clock clock;

    public LiveJwtValidator(LiveProperties properties, Clock clock) {
        this.issuer = properties.getJwt() != null ? properties.getJwt().getIssuer() : "https://issuer.educloud.local";
        this.audience = properties.getJwt() != null ? properties.getJwt().getAudience() : "educloud-api";
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Map<String, Object> claims = token.getClaims();
        if (!issuer.equals(claims.get("iss"))) {
            return failure("issuer", "Invalid token issuer");
        }
        Object audValue = claims.get("aud");
        if (!(audValue instanceof Collection<?> audiences)
                || !audiences.stream().allMatch(String.class::isInstance)) {
            return failure("audience", "Invalid token audience");
        }
        if (!audiences.contains(audience) && !audiences.contains("educloud-live")) {
            return failure("audience", "Audience mismatch for educloud-live");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }
}
