package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。portal 为三端门户标识（STUDENT/TEACHER/ADMIN），userType 需在 Gateway 校验器集合内。
 * 依据：API 规范第 7 节登录请求示例。
 */
public record LoginRequest(
        @NotBlank
        @Size(max = 128)
        String loginName,

        @NotBlank
        @Size(max = 128)
        String password,

        @NotNull
        Portal portal) {

    public enum Portal {
        STUDENT,
        TEACHER,
        ADMIN
    }
}
