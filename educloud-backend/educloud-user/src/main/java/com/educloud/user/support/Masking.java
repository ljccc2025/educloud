package com.educloud.user.support;

/**
 * 敏感字段脱敏（登录名/邮箱/手机，可靠性设计第 8.2 节）。
 */
public final class Masking {

    private Masking() {
    }

    public static String loginName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 2) {
            return trimmed.substring(0, 1) + "***";
        }
        if (trimmed.contains("@")) {
            String[] parts = trimmed.split("@", 2);
            String local = parts[0];
            String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
            return visible + "***@" + parts[1];
        }
        return trimmed.substring(0, Math.min(2, trimmed.length())) + "***";
    }

    public static String userIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 3) {
            return "***";
        }
        return trimmed.substring(0, 3) + "***";
    }
}
