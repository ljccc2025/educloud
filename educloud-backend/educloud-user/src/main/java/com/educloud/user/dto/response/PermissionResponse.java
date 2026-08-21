package com.educloud.user.dto.response;

/** 权限目录项。 */
public record PermissionResponse(
        String id,
        String code,
        String name,
        String resource,
        String action,
        String description) {
}
