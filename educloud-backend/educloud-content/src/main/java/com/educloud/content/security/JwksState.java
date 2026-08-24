package com.educloud.content.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class JwksState {
    private final Set<String> loadedKeyIds = new HashSet<>();

    public synchronized void markLoaded(Set<String> keyIds) {
        loadedKeyIds.clear();
        if (keyIds != null) {
            loadedKeyIds.addAll(keyIds);
        }
    }

    public synchronized Set<String> loadedKeyIds() {
        return Collections.unmodifiableSet(new HashSet<>(loadedKeyIds));
    }
}
