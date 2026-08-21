package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.user.dto.request.ProfileUpdateRequest;
import com.educloud.user.dto.response.ProfileResponse;
import com.educloud.user.dto.response.UserSummary;
import com.educloud.user.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户端点。依据：API 规范第 7 节（GET /me、PATCH /me/profile）。
 * 用户身份来自已验证 JWT 的 sub（服务端不接受客户端身份头，设计规格第 10 节）。
 */
@RestController
@RequestMapping("/api/v1/me")
public final class MeController {

    private final ProfileService profileService;
    private final ApiResponseFactory responses;

    public MeController(ProfileService profileService, ApiResponseFactory responses) {
        this.profileService = profileService;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<UserSummary> me(@AuthenticationPrincipal Jwt jwt) {
        return responses.success(profileService.me(userId(jwt)));
    }

    @PatchMapping("/profile")
    public ApiResponse<ProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileUpdateRequest request,
            HttpServletRequest servletRequest) {
        return responses.success(profileService.updateProfile(
                userId(jwt),
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                com.educloud.user.support.RequestIds.from(servletRequest)));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
