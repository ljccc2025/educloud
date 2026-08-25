package com.educloud.notification.security;

import com.educloud.notification.config.NotificationProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class NotificationJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationJwtValidator(NotificationProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT is null", null));
        }

        String expectedAudience = properties.getJwt() != null ? properties.getJwt().getAudience() : "educloud-api";
        List<String> audience = jwt.getAudience();
        if (expectedAudience != null && !expectedAudience.isBlank()) {
            if (audience == null || !audience.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience", "Audience does not match", null));
            }
        }

        return OAuth2TokenValidatorResult.success();
    }
}
