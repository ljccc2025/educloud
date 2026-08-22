package com.educloud.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 单文件下载授权请求体（POST /internal/v1/files/{id}/download-grants）。
 *
 * <p>依据：API 规范 14 节 —— ownerService 不接收客户端输入；requestedTtlSeconds 缺省由
 * 服务端默认 TTL 兜底（null → 服务端默认）。</p>
 */
public record DownloadGrantRequest(
        @NotBlank String subjectType,
        Long subjectUserId,
        @NotBlank String ownerType,
        @NotBlank String ownerId,
        @NotBlank String purpose,
        @PositiveOrZero Long requestedTtlSeconds) {
}
