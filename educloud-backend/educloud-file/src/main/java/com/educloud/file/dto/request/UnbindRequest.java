package com.educloud.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 解绑请求体（POST /internal/v1/files/{id}/unbind）。
 */
public record UnbindRequest(
        @NotBlank @Size(max = 64) String ownerType,
        @NotBlank @Size(max = 128) String ownerId) {
}
