package com.educloud.search.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 记录 JWKS 密钥加载状态与 Key ID 集合
 */
public final class JwksState {

    private final Set<String> loadedKeyIds = new HashSet<>();
    private volatile boolean loaded = false;

    public synchronized void markLoaded(Set<String> keyIds) {
        if (keyIds != null) {
            this.loadedKeyIds.addAll(keyIds);
        }
        this.loaded = true;
    }

    public synchronized Set<String> getLoadedKeyIds() {
        return Collections.unmodifiableSet(new HashSet<>(loadedKeyIds));
    }

    public boolean isLoaded() {
        return loaded;
    }
}
