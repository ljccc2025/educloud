package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 注册限流配置（educloud.user.registration.rate-limit）。依据：M03 注册限流设计 4.3 节。 */
@ConfigurationProperties("educloud.user.registration.rate-limit")
public final class RegistrationRateLimitProperties {

    private boolean enabled = true;
    private int ipMaxAttempts = 5;
    private int deviceMaxAttempts = 3;
    private Duration window = Duration.ofMinutes(5);
    private String redisKeyPrefix = "educloud:{env}:ratelimit";

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int ipMaxAttempts() { return ipMaxAttempts; }
    public void setIpMaxAttempts(int ipMaxAttempts) { this.ipMaxAttempts = ipMaxAttempts; }
    public int deviceMaxAttempts() { return deviceMaxAttempts; }
    public void setDeviceMaxAttempts(int deviceMaxAttempts) { this.deviceMaxAttempts = deviceMaxAttempts; }
    public Duration window() { return window; }
    public void setWindow(Duration window) { this.window = window; }
    public String redisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
}
