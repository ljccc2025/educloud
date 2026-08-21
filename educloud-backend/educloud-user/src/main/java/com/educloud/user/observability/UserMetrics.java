package com.educloud.user.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * User 服务业务指标。依据：M03 设计规格第 12 节（登录成功/失败、刷新轮换、重用检测、
 * 注册、撤销；低基数 tag）。
 */
@Component
public final class UserMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter refreshRotated;
    private final Counter sessionReuseDetected;
    private final Counter userRegistered;
    private final Counter sessionRevoked;

    public UserMetrics(MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.loginSuccess = Counter.builder("educloud.user.login.success")
                .description("Successful user logins").register(meterRegistry);
        this.loginFailure = Counter.builder("educloud.user.login.failure")
                .description("Failed user logins").register(meterRegistry);
        this.refreshRotated = Counter.builder("educloud.user.refresh.rotated")
                .description("Refresh tokens rotated").register(meterRegistry);
        this.sessionReuseDetected = Counter.builder("educloud.user.session.reuse_detected")
                .description("Refresh token reuse detections").register(meterRegistry);
        this.userRegistered = Counter.builder("educloud.user.registered")
                .description("Student registrations").register(meterRegistry);
        this.sessionRevoked = Counter.builder("educloud.user.session.revoked")
                .description("Revoked session families").register(meterRegistry);
    }

    public void loginSuccess() {
        loginSuccess.increment();
    }

    public void loginFailure() {
        loginFailure.increment();
    }

    public void refreshRotated() {
        refreshRotated.increment();
    }

    public void sessionReuseDetected() {
        sessionReuseDetected.increment();
    }

    public void userRegistered() {
        userRegistered.increment();
    }

    public void sessionRevoked() {
        sessionRevoked.increment();
    }
}
