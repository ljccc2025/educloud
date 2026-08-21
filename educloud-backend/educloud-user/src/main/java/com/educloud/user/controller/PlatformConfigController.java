package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.user.dto.request.PlatformConfigUpdateRequest;
import com.educloud.user.dto.response.PlatformConfigResponse;
import com.educloud.user.service.PlatformConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台公开配置接口（匿名读 / platform:config:update 写；API 规范第 7 节）。 */
@RestController
@RequestMapping("/api/v1/platform-config")
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;
    private final ApiResponseFactory responses;

    public PlatformConfigController(PlatformConfigService platformConfigService, ApiResponseFactory responses) {
        this.platformConfigService = platformConfigService;
        this.responses = responses;
    }

    @GetMapping("/public")
    public ApiResponse<List<PlatformConfigResponse>> publicConfigs() {
        return responses.success(platformConfigService.publicConfigs());
    }

    @PutMapping("/public")
    @PreAuthorize("hasAuthority('platform:config:update')")
    public ApiResponse<PlatformConfigResponse> update(
            @Valid @RequestBody PlatformConfigUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return responses.success(platformConfigService.update(
                request,
                jwt.getSubject(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                com.educloud.user.support.RequestIds.from(servletRequest)));
    }
}
