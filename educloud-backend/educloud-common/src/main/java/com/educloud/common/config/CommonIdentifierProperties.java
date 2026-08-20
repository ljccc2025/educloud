package com.educloud.common.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("educloud.common.id")
public class CommonIdentifierProperties {

    private boolean enabled;

    @NotNull
    private Duration leaseTtl = Duration.ofSeconds(30);

    @NotNull
    private Duration renewalInterval = Duration.ofSeconds(10);

    @NotNull
    private Duration clockBackwardTolerance = Duration.ofMillis(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getLeaseTtl() {
        return leaseTtl;
    }

    public void setLeaseTtl(Duration leaseTtl) {
        this.leaseTtl = leaseTtl;
    }

    public Duration getRenewalInterval() {
        return renewalInterval;
    }

    public void setRenewalInterval(Duration renewalInterval) {
        this.renewalInterval = renewalInterval;
    }

    public Duration getClockBackwardTolerance() {
        return clockBackwardTolerance;
    }

    public void setClockBackwardTolerance(Duration clockBackwardTolerance) {
        this.clockBackwardTolerance = clockBackwardTolerance;
    }

    @AssertTrue(message = "durations must be non-negative and renewal-interval must be less than lease-ttl")
    public boolean isDurationConfigurationValid() {
        if (leaseTtl == null || renewalInterval == null || clockBackwardTolerance == null) {
            return true;
        }
        return !leaseTtl.isNegative()
                && !leaseTtl.isZero()
                && !renewalInterval.isNegative()
                && !renewalInterval.isZero()
                && !clockBackwardTolerance.isNegative()
                && renewalInterval.compareTo(leaseTtl) < 0;
    }
}
