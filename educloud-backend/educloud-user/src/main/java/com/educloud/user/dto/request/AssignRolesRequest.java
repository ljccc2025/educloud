package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 角色分配请求。 */
public record AssignRolesRequest(
        @NotEmpty
        List<@NotBlank String> roleCodes) {
}
