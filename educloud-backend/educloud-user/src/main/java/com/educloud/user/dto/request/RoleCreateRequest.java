package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 角色创建请求。 */
public record RoleCreateRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9_]+", message = "role code may only contain uppercase letters, digits and underscore")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 255)
        String description) {
}
