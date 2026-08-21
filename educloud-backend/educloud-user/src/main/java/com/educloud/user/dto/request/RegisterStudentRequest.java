package com.educloud.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 学生自助注册请求。依据：M03 设计规格第 10.1 节与 API 规范（Bean Validation 全量校验）。
 */
public record RegisterStudentRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "username may only contain letters, digits, dot, underscore and hyphen")
        String username,

        @NotBlank
        @Size(min = 1, max = 128)
        String password,

        @Email
        @Size(max = 128)
        String email,

        @Pattern(regexp = "^[0-9+ -]{5,32}$", message = "phone is invalid")
        String phone,

        @Size(max = 64)
        String displayName) {
}
