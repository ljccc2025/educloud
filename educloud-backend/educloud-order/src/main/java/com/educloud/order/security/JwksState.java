package com.educloud.order.security;

import java.util.concurrent.atomic.AtomicLong;

public class JwksState {

    private final AtomicLong lastLoadedTime = new AtomicLong(0L);

    public void updateLoadedTime() {
        lastLoadedTime.set(System.currentTimeMillis());
    }

    public long getLastLoadedTime() {
        return lastLoadedTime.get();
    }
}
