package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 本人档案更新请求。依据：API 规范第 7 节（PATCH /me/profile）。
 * avatarFileId 仅记录 File 对象 ID；M03 不调用 File 服务（M04 接线，设计规格第 3.1 节）。
 */
public record ProfileUpdateRequest(
        @NotBlank
        @Size(max = 64)
        String displayName,

        @Size(max = 500)
        String bio,

        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$", message = "locale is invalid")
        String locale,

        @Positive
        Long avatarFileId) {
}
