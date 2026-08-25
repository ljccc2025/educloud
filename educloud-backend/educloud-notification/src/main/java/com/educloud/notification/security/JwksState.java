package com.educloud.notification.security;

import java.time.Instant;

public class JwksState {

    private volatile Instant lastLoadedAt;

    public void updateLoadedTime() {
        this.lastLoadedAt = Instant.now();
    }

    public Instant getLastLoadedAt() {
        return lastLoadedAt;
    }
}
