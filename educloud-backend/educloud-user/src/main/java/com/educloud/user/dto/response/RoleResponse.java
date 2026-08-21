package com.educloud.user.dto.response;

/** 角色响应。 */
public record RoleResponse(
        String id,
        String code,
        String name,
        String description,
        String status,
        boolean builtIn) {
}
