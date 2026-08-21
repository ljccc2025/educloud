package com.educloud.user.dto.response;

import java.util.List;

/** 登录/当前用户摘要。依据：API 规范第 7 节（登录响应 data 中的用户摘要）。 */
public record UserSummary(
        String id,
        String username,
        String displayName,
        String userType,
        List<String> roles,
        List<String> permissions) {
}
