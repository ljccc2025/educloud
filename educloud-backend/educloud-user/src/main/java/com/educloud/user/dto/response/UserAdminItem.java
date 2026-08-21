package com.educloud.user.dto.response;

import java.time.Instant;

/** 管理端用户列表项（手机/邮箱脱敏；API 规范第 7 节）。 */
public record UserAdminItem(
        String id,
        String username,
        String emailMasked,
        String phoneMasked,
        String userType,
        String status,
        String displayName,
        Instant createdAt,
        Integer version) {
}
