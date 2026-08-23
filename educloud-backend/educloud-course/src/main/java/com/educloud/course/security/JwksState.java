package com.educloud.course.security;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** JWKS 加载状态（复制 gateway/file 同名类）：供健康检查/运维观测。 */
public final class JwksState {

    private final AtomicReference<Set<String>> keyIds = new AtomicReference<>();

    public boolean loaded() {
        return keyIds.get() != null;
    }

    public int keyCount() {
        Set<String> current = keyIds.get();
        return current == null ? 0 : current.size();
    }

    void markLoaded(Set<String> loadedKeyIds) {
        keyIds.set(Set.copyOf(loadedKeyIds));
    }
}
