package com.educloud.user.dto.response;

import java.util.List;

/**
 * 登录/当前用户摘要。依据：API 规范第 7 节（登录响应 data 中的用户摘要）。
 * avatarUrl 为 File 服务短期授权 URL（M04 任务 14）；登录/刷新路径不解析，保持 null。
 */
public record UserSummary(
        String id,
        String username,
        String displayName,
        String userType,
        List<String> roles,
        List<String> permissions,
        String avatarUrl) {
}
