package com.educloud.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("educloud.gateway.ratelimit")
@Validated
public final class GatewayRateLimitProperties {

    @NotBlank
    private String hmacSecretBase64;

    @Valid
    @NotNull
    private Bucket ordinary = new Bucket(20, Duration.ofSeconds(1), 40);

    @Valid
    @NotNull
    private Bucket loginIp = new Bucket(10, Duration.ofMinutes(1), 10);

    @Valid
    @NotNull
    private Bucket loginAccount = new Bucket(5, Duration.ofMinutes(5), 5);

    @Valid
    @NotNull
    private Bucket paymentCallback = new Bucket(60, Duration.ofMinutes(1), 60);

    @AssertTrue(message = "hmacSecretBase64 must be valid Base64 and decode to at least 32 bytes")
    public boolean isHmacSecretStrong() {
        if (hmacSecretBase64 == null || hmacSecretBase64.isBlank()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(hmacSecretBase64).length >= 32;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public String getHmacSecretBase64() {
        return hmacSecretBase64;
    }

    public void setHmacSecretBase64(String hmacSecretBase64) {
        this.hmacSecretBase64 = hmacSecretBase64;
    }

    public Bucket getOrdinary() {
        return ordinary;
    }

    public void setOrdinary(Bucket ordinary) {
        this.ordinary = ordinary;
    }

    public Bucket getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(Bucket loginIp) {
        this.loginIp = loginIp;
    }

    public Bucket getLoginAccount() {
        return loginAccount;
    }

    public void setLoginAccount(Bucket loginAccount) {
        this.loginAccount = loginAccount;
    }

    public Bucket getPaymentCallback() {
        return paymentCallback;
    }

    public void setPaymentCallback(Bucket paymentCallback) {
        this.paymentCallback = paymentCallback;
    }

    public record Bucket(
            @Positive(message = "requests must be positive") int requests,
            @NotNull Duration period,
            @Positive(message = "burst must be positive") int burst) {

        @AssertTrue(message = "period must be positive")
        public boolean isPeriodPositive() {
            return period != null && !period.isZero() && !period.isNegative();
        }

        @AssertTrue(message = "burst must be greater than or equal to requests")
        public boolean isBurstCapacityValid() {
            return burst >= requests;
        }
    }
}
