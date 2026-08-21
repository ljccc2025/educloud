package com.educloud.gateway.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("educloud.gateway.security")
@Validated
public final class GatewaySecurityProperties {

    private String jwksJson;
    private Resource jwksLocation;

    @NotBlank
    private String issuer;

    @NotBlank
    private String audience = "educloud-api";

    @NotNull
    private Duration clockSkew = Duration.ofSeconds(30);

    @AssertTrue(message = "exactly one JWKS source must be configured")
    public boolean isExactlyOneJwksSourceConfigured() {
        boolean hasJson = StringUtils.hasText(jwksJson);
        boolean hasLocation = jwksLocation != null;
        return hasJson ^ hasLocation;
    }

    @AssertTrue(message = "clockSkew must be between 0 and 120 seconds")
    public boolean isClockSkewValid() {
        return clockSkew != null
                && !clockSkew.isNegative()
                && clockSkew.compareTo(Duration.ofSeconds(120)) <= 0;
    }

    public String getJwksJson() {
        return jwksJson;
    }

    public void setJwksJson(String jwksJson) {
        this.jwksJson = jwksJson;
    }

    public Resource getJwksLocation() {
        return jwksLocation;
    }

    public void setJwksLocation(Resource jwksLocation) {
        this.jwksLocation = jwksLocation;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }
}
