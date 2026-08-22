package com.educloud.file.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 解绑请求体（POST /internal/v1/files/{id}/unbind）。
 */
public record UnbindRequest(
        @NotBlank String ownerType,
        @NotBlank String ownerId) {
}
