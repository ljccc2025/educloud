package com.educloud.notification.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.notification.dto.response.NotificationResponse;
import com.educloud.notification.dto.response.UnreadCountResponse;
import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.security.JwtSecurityUtils;
import com.educloud.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final ApiResponseFactory responses;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NotificationKind kind,
            @RequestParam(required = false) Boolean unreadOnly) {
        Long userId = JwtSecurityUtils.userId(jwt);
        PageResponse<NotificationResponse> result = notificationService.getMyNotifications(userId, page, size, kind, unreadOnly);
        return responses.success(result);
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtSecurityUtils.userId(jwt);
        UnreadCountResponse response = notificationService.getUnreadCount(userId);
        return responses.success(response);
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = JwtSecurityUtils.userId(jwt);
        notificationService.markAsRead(userId, id);
        return responses.success(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtSecurityUtils.userId(jwt);
        notificationService.markAllAsRead(userId);
        return responses.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = JwtSecurityUtils.userId(jwt);
        notificationService.deleteNotification(userId, id);
        return responses.success(null);
    }
}
