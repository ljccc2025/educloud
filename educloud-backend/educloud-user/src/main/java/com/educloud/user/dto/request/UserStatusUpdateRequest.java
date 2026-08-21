package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 管理端用户状态更新请求（乐观锁 version；API 规范第 7 节）。 */
public record UserStatusUpdateRequest(
        @NotBlank
        @Size(max = 16)
        String status,

        @NotNull
        Integer version,

        @Size(max = 255)
        String reason) {
}
