package com.educloud.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 绑定请求体（POST /internal/v1/files/{id}/bind）。
 *
 * <p>ownerService 不由客户端提供，恒由已认证 clientId 推导（规格 6.2 节）。</p>
 */
public record BindRequest(
        @NotBlank @Size(max = 64) String ownerType,
        @NotBlank @Size(max = 128) String ownerId) {
}
