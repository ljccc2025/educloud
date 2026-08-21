package com.educloud.user.support;

import com.educloud.user.session.SessionFactory;

/**
 * 客户端指纹：归一化 User-Agent 的 SHA-256（安全设计第 3.2 节，跨端重用检测用）。
 */
public final class ClientFingerprint {

    private ClientFingerprint() {
    }

    public static String of(String userAgent) {
        String normalized = userAgent == null ? "" : userAgent.trim().toLowerCase(java.util.Locale.ROOT);
        return SessionFactory.sha256Hex(normalized);
    }
}
