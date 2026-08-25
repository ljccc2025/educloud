package com.educloud.notification.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.notification.dto.request.EmailTestSendRequest;
import com.educloud.notification.dto.response.EmailChannelStatusResponse;
import com.educloud.notification.security.JwtSecurityUtils;
import com.educloud.notification.service.EmailChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-channels/email")
@RequiredArgsConstructor
public class EmailChannelStatusController {

    private final EmailChannelService emailChannelService;
    private final ApiResponseFactory responses;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('notification:channel:view') or hasAuthority('ADMIN')")
    public ApiResponse<EmailChannelStatusResponse> getStatus() {
        EmailChannelStatusResponse response = emailChannelService.getEmailChannelStatus();
        return responses.success(response);
    }

    @PostMapping("/test-send")
    @PreAuthorize("hasAuthority('notification:channel:test') or hasAuthority('ADMIN')")
    public ApiResponse<Void> testSend(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) EmailTestSendRequest request) {
        Long adminUserId = JwtSecurityUtils.userId(jwt);
        String adminEmail = JwtSecurityUtils.email(jwt);
        emailChannelService.testSendEmail(adminUserId, adminEmail, request);
        return responses.success(null);
    }
}
