package com.educloud.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。旧密码校验 + 新哈希 + 撤销矩阵（设计规格第 4.4 节、API 规范第 7 节）。
 * 新密码最小长度由服务层 PasswordPolicy 按配置校验（与注册/登录一致）。
 */
public record PasswordChangeRequest(
        @NotBlank
        @Size(max = 128)
        String oldPassword,

        @NotBlank
        @Size(max = 128)
        String newPassword) {
}
