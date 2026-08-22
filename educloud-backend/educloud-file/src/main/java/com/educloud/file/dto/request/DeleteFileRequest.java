package com.educloud.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 内部删除请求体（POST /internal/v1/files/{id}/delete）。
 *
 * <p>reason 保留供审计与事件载荷使用（任务 11/12）。</p>
 */
public record DeleteFileRequest(
        @NotBlank String ownerType,
        @NotBlank String ownerId,
        @NotBlank @Size(max = 255) String reason) {
}
