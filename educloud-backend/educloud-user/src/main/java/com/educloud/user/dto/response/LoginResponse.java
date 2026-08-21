package com.educloud.user.dto.response;

/**
 * 登录/刷新响应 data。Refresh Token 只通过 HttpOnly Cookie 返回，永不进入响应体
 * （安全设计第 3.2 节、API 规范第 7 节）。
 */
public record LoginResponse(String accessToken, long expiresIn, UserSummary user) {
}
