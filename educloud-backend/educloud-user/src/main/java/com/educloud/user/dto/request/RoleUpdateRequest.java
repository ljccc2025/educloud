package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 角色更新请求（内置角色不可改 code）。 */
public record RoleUpdateRequest(
        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 255)
        String description) {
}
