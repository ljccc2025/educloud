package com.educloud.notification.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.notification.dto.request.PublishNotificationRequest;
import com.educloud.notification.dto.response.NotificationResponse;
import com.educloud.notification.security.JwtSecurityUtils;
import com.educloud.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final ApiResponseFactory responses;

    @PostMapping
    @PreAuthorize("hasAuthority('notification:publish') or hasAuthority('ADMIN')")
    public ApiResponse<NotificationResponse> publishNotification(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PublishNotificationRequest request) {
        Long adminUserId = JwtSecurityUtils.userId(jwt);
        NotificationResponse response = notificationService.publishNotification(adminUserId, request);
        return responses.success(response);
    }
}
