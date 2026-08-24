package com.educloud.content.security;

import com.educloud.content.config.ContentProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ContentJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Set<String> USER_TYPES = Set.of("STUDENT", "TEACHER", "ADMIN");
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final String issuer;
    private final String audience;
    private final String internalAudience;
    private final Clock clock;

    public ContentJwtValidator(ContentProperties properties, Clock clock) {
        this.issuer = properties.jwt() != null ? properties.jwt().issuer() : "https://issuer.educloud.local";
        this.audience = properties.jwt() != null ? properties.jwt().audience() : "educloud-api";
        this.internalAudience = properties.internal() != null ? properties.internal().effectiveInternalAudience() : "educloud-content";
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Map<String, Object> claims = token.getClaims();
        if (issuer != null && !issuer.equals(claims.get("iss"))) {
            return failure("issuer");
        }
        Object audValue = claims.get("aud");
        if (audValue instanceof String audStr) {
            if (audStr.equals(audience) || audStr.equals(internalAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
        }
        if (audValue instanceof Collection<?> audiences) {
            if (audiences.contains(audience) || audiences.contains(internalAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
